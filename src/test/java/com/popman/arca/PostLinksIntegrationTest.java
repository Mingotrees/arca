package com.popman.arca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popman.arca.entity.User;
import com.popman.arca.entity.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class PostLinksIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void seed() {
        for (String table : new String[]{"files", "votes", "vaults", "post_tags", "posts", "post_identities",
                "user_roles", "refresh_tokens", "department_subjects", "subject_aliases", "subjects", "departments", "school", "users"})
            jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO users(id,email,is_deleted) VALUES(1,'owner@example.test',false),(2,'other@example.test',false),(3,'admin@example.test',false)");
        jdbc.update("INSERT INTO user_roles(user_id,role) VALUES(1,'ROLE_USER'),(2,'ROLE_USER'),(3,'ROLE_ADMIN')");
        jdbc.update("INSERT INTO school(id,name) VALUES(1,'School')");
        jdbc.update("INSERT INTO departments(id,name,school_id) VALUES(1,'Department',1)");
    }

    @Test
    void linksRoundTripInOrderAndVersionsCopyReplaceClearWithoutChangingHistory() throws Exception {
        JsonNode created = create(List.of(Map.of("url", "https://Example.test/b", "label", " Second "), Map.of("url", "https://example.test/a")));
        long row = created.path("id").asLong(); int logical = created.path("post_id").asInt();
        JsonNode original = read(row, 1).path("links");
        assertEquals(2, original.size(), "Created post must retain ordered links");
        assertEquals("https://Example.test/b", original.get(0).path("url").asText());
        assertEquals("Second", original.get(0).path("label").asText());
        assertEquals(0, original.get(0).path("position").asInt());
        assertEquals("https://example.test/a", original.get(1).path("url").asText());
        assertEquals(1, original.get(1).path("position").asInt());
        approve(row);
        JsonNode copy = update(row, Map.of("title", "Copied"), 1, 200);
        long copiedRow = copy.path("id").asLong();
        assertEquals(original, read(copiedRow, 1).path("links"));
        assertNotEquals(jdbc.queryForList("SELECT id FROM post_links WHERE post_id=? ORDER BY position", Long.class, row),
                jdbc.queryForList("SELECT id FROM post_links WHERE post_id=? ORDER BY position", Long.class, copiedRow));
        approve(copiedRow);
        JsonNode replaced = update(copiedRow, Map.of("links", List.of(Map.of("url", "http://example.test/new", "label", "New"))), 3, 200);
        long replacedRow = replaced.path("id").asLong();
        assertEquals("http://example.test/new", read(replacedRow, 1).path("links").get(0).path("url").asText());
        assertEquals(original, read(copiedRow, 1).path("links"));
        approve(replacedRow);
        long clearedRow = update(replacedRow, Map.of("links", List.of()), 1, 200).path("id").asLong();
        assertEquals(0, read(clearedRow, 1).path("links").size());
        JsonNode history = json(mvc.perform(get("/api/v1/posts/history/" + logical).with(user(actor(1)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(4, history.size());
        assertEquals(original, history.get(3).path("links"));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM post_links", Integer.class));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM post_links WHERE created_at IS NOT NULL AND updated_at=created_at", Integer.class));
    }

    @Test
    void legacyClientsReceiveEmptyLinksAndNullUpdateRetainsLinks() throws Exception {
        long legacy = create(null).path("id").asLong();
        assertTrue(read(legacy, 1).path("links").isArray());
        assertEquals(0, read(legacy, 1).path("links").size());
        long row = create(List.of(Map.of("url", "https://example.test"))).path("id").asLong();
        approve(row);
        var body = new java.util.HashMap<String, Object>(); body.put("links", null);
        long next = update(row, body, 1, 200).path("id").asLong();
        assertEquals(read(row, 1).path("links"), read(next, 1).path("links"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "data:text/html,hello", "//example.test/path", "ftp://example.test", "https://user:pass@example.test/", "https://example.test/%0aInjected", "https://example.test/has space", "https://bad_host.test/", "https://example.test:70000", "https://example.test/%zz", "https://[::1", "https://example.test/\\evil", "https://\uD800.test/"})
    void invalidUrlsReturn400BeforeAnyPostIdentityOrLinksPersist(String url) throws Exception {
        mvc.perform(post("/api/v1/posts").with(user(actor(1))).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body(List.of(Map.of("url", url)))))).andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM post_identities", Integer.class));
    }

    @Test
    void duplicatesAndOversizedListsUrlsLabelsAndNullItemsFailAtomically() throws Exception {
        var invalidSets = new java.util.ArrayList<Object>();
        invalidSets.add(List.of(Map.of("url", "HTTPS://Example.test:443/a/../b"), Map.of("url", "https://example.test/b")));
        invalidSets.add(java.util.Collections.nCopies(11, Map.of("url", "https://example.test/")));
        invalidSets.add(List.of(Map.of("url", "https://example.test/" + "x".repeat(2048))));
        invalidSets.add(List.of(Map.of("url", "https://example.test/", "label", "x".repeat(201))));
        invalidSets.add(java.util.Arrays.asList((Object) null));
        invalidSets.add(List.of(Map.of("label", "Missing URL")));
        long row = create(List.of(Map.of("url", "https://example.test/old"))).path("id").asLong();
        approve(row);
        for (Object links : invalidSets) {
            update(row, Map.of("links", links), 1, 400);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM post_links", Integer.class));
            assertTrue(jdbc.queryForObject("SELECT is_latest_version FROM posts WHERE id=?", Boolean.class, row));
        }
    }

    @Test
    void privateAndDeletedLinksFollowParentVisibilityAndFailedOuterTransactionRollsBack() throws Exception {
        long row = create(List.of(Map.of("url", "https://private.example.test/secret"))).path("id").asLong();
        int logical = jdbc.queryForObject("SELECT post_id FROM posts WHERE id=?", Integer.class, row);
        mvc.perform(get("/api/v1/posts/" + row)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/posts/" + row).with(user(actor(2)))).andExpect(status().isNotFound());
        update(row, Map.of("links", List.of()), 2, 404);
        mvc.perform(get("/api/v1/posts/history/" + logical).with(user(actor(2)))).andExpect(status().isOk()).andExpect(content().json("[]"));
        approve(row);
        update(row, Map.of("links", List.of()), 2, 403);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            try { update(row, Map.of("links", List.of(Map.of("url", "https://example.test/rolled-back"))), 1, 200); }
            catch (Exception e) { throw new RuntimeException(e); }
            tx.setRollbackOnly();
        });
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM post_links", Integer.class));
        mvc.perform(get("/api/v1/posts/search").with(user(actor(2)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].links[0].url").value("https://private.example.test/secret"));
        mvc.perform(delete("/api/v1/posts/" + row).with(user(actor(1)))).andExpect(status().isNoContent());
        for (long who : new long[]{1, 2, 3}) {
            mvc.perform(get("/api/v1/posts/" + row).with(user(actor(who)))).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/posts/history/" + logical).with(user(actor(who)))).andExpect(status().isOk()).andExpect(content().json("[]"));
            mvc.perform(get("/api/v1/posts/search").with(user(actor(who)))).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM post_links", Integer.class));
    }

    private Map<String, Object> body(Object links) {
        var body = new java.util.HashMap<String, Object>(Map.of("title", "Linked post", "content", "Existing https://inline.test stays", "department_id", 1));
        if (links != null) body.put("links", links);
        return body;
    }

    @Test
    void submittedDestinationIsNeverContactedAndLabelsRemainPlainText() throws Exception {
        var hits = new java.util.concurrent.atomic.AtomicInteger();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> { hits.incrementAndGet(); exchange.sendResponseHeaders(204, -1); exchange.close(); });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/untrusted";
            long row = create(List.of(Map.of("url", url, "label", "<script>plain label</script>"))).path("id").asLong();
            assertEquals("<script>plain label</script>", read(row, 1).path("links").get(0).path("label").asText());
            assertEquals(0, hits.get(), "Submitting/reading a link must never fetch its destination");
        } finally { server.stop(0); }
    }
    private JsonNode create(Object links) throws Exception {
        return json(mvc.perform(post("/api/v1/posts").with(user(actor(1))).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body(links)))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode update(long row, Map<String, Object> body, long who, int expected) throws Exception {
        return json(mvc.perform(put("/api/v1/posts/" + row).with(user(actor(who))).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }
    private JsonNode read(long row, long who) throws Exception {
        return json(mvc.perform(get("/api/v1/posts/" + row).with(user(actor(who)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private void approve(long row) throws Exception {
        mvc.perform(post("/api/v1/posts/" + row + "/approve").with(user(actor(3))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approved\":true}")).andExpect(status().isOk());
    }
    private JsonNode json(String body) throws Exception { return mapper.readTree(body); }
    private UserPrincipal actor(long id) {
        User user = new User(); user.setId(id); user.setEmail(id + "@example.test"); user.addRole(id == 3 ? "ROLE_ADMIN" : "ROLE_USER");
        return new UserPrincipal(user);
    }
}
