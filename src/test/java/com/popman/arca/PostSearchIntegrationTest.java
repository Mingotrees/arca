package com.popman.arca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popman.arca.entity.User;
import com.popman.arca.entity.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.stream.StreamSupport;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "logging.level.com.popman.arca.filter.PostDeletionObservationFilter=INFO")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class PostSearchIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired io.micrometer.core.instrument.MeterRegistry meters;
    private static final String SEARCH = "/api/v1/posts/search";

    @BeforeEach
    void seed() {
        for (String table : new String[]{"files", "votes", "vaults", "post_tags", "posts", "post_identities",
                "user_roles", "refresh_tokens", "department_subjects", "subject_aliases", "subjects",
                "departments", "school", "users"}) jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO users(id,email,first_name,is_deleted) VALUES "
                + "(1,'owner@example.test','Owner',false),(2,'reader@example.test','Reader',false),(3,'admin@example.test','Admin',false)");
        jdbc.update("INSERT INTO user_roles(user_id,role) VALUES(1,'ROLE_USER'),(2,'ROLE_USER'),(3,'ROLE_ADMIN')");
        jdbc.update("INSERT INTO school(id,name) VALUES(1,'School')");
        jdbc.update("INSERT INTO departments(id,name,school_id) VALUES(1,'Science',1),(2,'Mathematics',1)");
        jdbc.update("INSERT INTO subjects(id,code,name) VALUES(1,'PHY','Physics'),(2,'MAT','Math'),(3,'LIT','Literature')");
        for (int id = 10; id <= 18; id++) {
            if (id != 14) jdbc.update("INSERT INTO post_identities(id,owner_id) VALUES(?,?)", id, id == 11 ? 2 : 1);
        }
        insert(10, 10, 1, "Quantum fields", "quantum quantum quantum quantum photon", "APPROVED", 1, 1, true, false, "2026-01-01 00:00:00");
        insert(11, 11, 1, "Quantum mechanics", "electron mechanics", "APPROVED", 2, 1, true, false, "2026-01-02 00:00:00");
        insert(12, 12, 1, "Algebra notes", "matrix algebra", "APPROVED", 1, 2, true, false, "2026-01-02 00:00:00");
        insert(13, 13, 1, "Café lecture", "Résumé café electron", "APPROVED", 1, 1, true, false, "2026-01-03 00:00:00");
        insert(14, 10, 2, "Private quantum", "quantum secret", "PENDING_APPROVAL", 1, 1, true, false, "2026-01-04 00:00:00");
        insert(15, 15, 1, "Rejected quantum", "quantum secret", "REJECTED", 1, 1, false, false, "2026-01-04 00:00:00");
        insert(16, 16, 1, "Superseded quantum", "quantum secret", "SUPERSEDED", 1, 1, false, false, "2026-01-04 00:00:00");
        insert(17, 17, 1, "Historical quantum", "quantum secret", "APPROVED", 1, 1, false, false, "2026-01-04 00:00:00");
        insert(18, 18, 1, "Deleted quantum", "quantum secret", "APPROVED", 1, 1, true, true, "2026-01-04 00:00:00");
        jdbc.update("INSERT INTO post_tags(post_id,subject_id) VALUES(10,1),(10,2),(11,1),(12,2),(13,1),(13,3)");
        jdbc.update("INSERT INTO votes(post_id,user_id,vote_type) VALUES(10,1,'UPVOTE'),(10,2,'UPVOTE'),"
                + "(11,1,'UPVOTE'),(11,2,'DOWNVOTE'),(12,2,'DOWNVOTE')");
        jdbc.update("INSERT INTO files(id,file_name,file_type,file_size,file_path,post_id,user_id) "
                + "VALUES(1,'notes.pdf','application/pdf',123,'uploads/posts/10/notes.pdf',10,1)");
        // Refresh corpus statistics after replacing this tiny synthetic full-text corpus.
        jdbc.execute("ANALYZE TABLE posts");
    }

    @Test
    void defaultSearchConcealsPrivateHistoricalAndDeletedRowsForEveryRole() throws Exception {
        mvc.perform(get(SEARCH)).andExpect(status().isUnauthorized());
        for (long actor : new long[]{1, 2, 3}) {
            JsonNode page = page(get(SEARCH), actor);
            assertEquals(List.of(13L, 12L, 11L, 10L), ids(page));
            assertEquals(4, page.path("total_elements").asLong());
            assertEquals(1, page.path("total_pages").asLong());
            assertEquals(20, page.path("size").asInt());
            assertEquals("newest", page.path("sort").asText());
        }
    }

    @ParameterizedTest
    @CsvSource({"subject_id,1,3", "subject_id,2,2", "department_id,1,3", "department_id,2,1",
            "author_id,1,3", "author_id,2,1", "author_id,999,0"})
    void filtersCountTheIntersectionWithoutSubjectDuplicates(String name, String value, long total) throws Exception {
        JsonNode page = page(get(SEARCH).param(name, value), 2);
        assertEquals(total, page.path("total_elements").asLong());
        assertEquals(total, ids(page).size());
        assertEquals(ids(page).size(), ids(page).stream().distinct().count());
    }

    @Test
    void combinedTextTaxonomyAuthorAndHalfOpenDateFiltersIntersect() throws Exception {
        JsonNode page = page(get(SEARCH).param("q", "quantum").param("subject_id", "1")
                .param("department_id", "1").param("author_id", "1")
                .param("from", "2026-01-01T00:00:00").param("to", "2026-01-03T00:00:00"), 2);
        assertEquals(List.of(10L), ids(page));
        assertEquals("relevance", page.path("sort").asText());
        assertEquals(List.of(12L, 11L), ids(page(get(SEARCH).param("from", "2026-01-02T00:00:00")
                .param("to", "2026-01-03T00:00:00"), 2)));
    }

    @Test
    void stablePagingRetainsFullResponsesAndCorrectLastAndEmptyPages() throws Exception {
        assertEquals(List.of(13L, 12L), ids(page(get(SEARCH).param("size", "2"), 2)));
        JsonNode second = page(get(SEARCH).param("size", "2").param("page", "1"), 2);
        assertEquals(List.of(11L, 10L), ids(second));
        assertEquals(2, second.path("total_pages").asLong());
        JsonNode post = second.path("content").get(1);
        assertEquals("Owner", post.path("first_name").asText());
        assertEquals("Science", post.path("department_name").asText());
        assertEquals(2, post.path("subjects").size());
        assertEquals(2, post.path("upvote_count").asInt());
        assertEquals("notes.pdf", post.path("files").get(0).path("file_name").asText());
        JsonNode empty = page(get(SEARCH).param("size", "2").param("page", "2"), 2);
        assertTrue(ids(empty).isEmpty());
        assertEquals(4, empty.path("total_elements").asLong());
        assertTrue(ids(page(get(SEARCH).param("page", "2147483647").param("size", "100"), 2)).isEmpty());
    }

    @Test
    void everySortHasDeterministicIdTieBreakers() throws Exception {
        assertEquals(List.of(10L, 11L, 12L, 13L), ids(page(get(SEARCH).param("sort", "oldest"), 2)));
        assertEquals(List.of(10L, 13L, 11L, 12L), ids(page(get(SEARCH).param("sort", "vote_score"), 2)));
        var relevance = get(SEARCH).param("q", "quantum").param("sort", "relevance");
        assertEquals(List.of(10L, 11L), ids(page(relevance, 2)));
        assertEquals(List.of(10L, 11L), ids(page(get(SEARCH).param("q", "quantum").param("sort", "relevance"), 2)));
    }

    @Test
    void naturalLanguageSearchHandlesBlankEscapedAndNonAsciiInput() throws Exception {
        assertEquals(List.of(13L, 12L, 11L, 10L), ids(page(get(SEARCH).param("q", "   "), 2)));
        assertEquals(List.of(13L), ids(page(get(SEARCH).param("q", "café"), 2)));
        assertEquals(List.of(13L), ids(page(get(SEARCH).param("q", "CAFE"), 2)));
        for (String q : new String[]{"%_", "' OR 1=1 --", "zzunknownword", "xy"}) {
            JsonNode empty = page(get(SEARCH).param("q", q), 2);
            assertTrue(ids(empty).isEmpty(), q);
            assertEquals(0, empty.path("total_elements").asLong());
            assertEquals(0, empty.path("total_pages").asLong());
        }
    }

    @Test
    void explicitModerationStatusRequiresAdminAndUsesNewestStoredRevision() throws Exception {
        for (long actor : new long[]{1, 2}) {
            mvc.perform(get(SEARCH).param("status", "PENDING_APPROVAL").with(user(principal(actor))))
                    .andExpect(status().isForbidden());
        }
        assertEquals(List.of(14L), ids(page(get(SEARCH).param("status", "PENDING_APPROVAL"), 3)));
        assertEquals(List.of(15L), ids(page(get(SEARCH).param("status", "REJECTED"), 3)));
        assertEquals(List.of(16L), ids(page(get(SEARCH).param("status", "SUPERSEDED"), 3)));
        assertEquals(List.of(13L, 12L, 11L, 10L), ids(page(get(SEARCH).param("status", "APPROVED"), 3)));
    }

    @Test
    void moderationStatusSearchNeverResurfacesOlderMatchingRevisions() throws Exception {
        insert(19, 10, 3, "Approved replacement", "quantum revised", "APPROVED", 1, 1, true, false, "2026-01-05 00:00:00");
        insert(20, 15, 2, "Approved after rejection", "quantum revised", "APPROVED", 1, 1, true, false, "2026-01-05 00:00:00");
        insert(21, 16, 2, "Newest pending", "quantum revised", "PENDING_APPROVAL", 1, 1, true, false, "2026-01-05 00:00:00");
        jdbc.update("UPDATE posts SET is_latest_version=false WHERE id IN (10,14)");
        assertEquals(List.of(21L), ids(page(get(SEARCH).param("status", "PENDING_APPROVAL"), 3)));
        for (String status : new String[]{"REJECTED", "SUPERSEDED"}) {
            JsonNode result = page(get(SEARCH).param("status", status), 3);
            assertEquals(List.of(), ids(result));
            assertEquals(0, result.path("total_elements").asLong());
        }
    }

    @Test
    void searchErrorBodiesMatchDocumentedJsonEnvelope() throws Exception {
        JsonNode contract;
        try (var input = java.nio.file.Files.newInputStream(java.nio.file.Path.of("openapi.yaml"))) {
            contract = mapper.valueToTree(new org.yaml.snakeyaml.Yaml().load(input));
        }
        for (int code : new int[]{400, 401, 403}) {
            var documented = contract.path("paths").path(SEARCH).path("get").path("responses").path(Integer.toString(code));
            assertTrue(documented.path("content").has("application/json"), "Error " + code + " must document its JSON body");
            String reference = documented.path("content").path("application/json").path("schema").path("$ref").asText();
            JsonNode schema = contract.at(reference.substring(1));
            assertEquals(java.util.Set.of("timestamp", "status", "error", "message"),
                    StreamSupport.stream(schema.path("required").spliterator(), false).map(JsonNode::asText).collect(java.util.stream.Collectors.toSet()));
            var request = get(SEARCH);
            if (code == 400) request.param("size", "101").with(user(principal(2)));
            if (code == 403) request.param("status", "APPROVED").with(user(principal(2)));
            JsonNode body = mapper.readTree(mvc.perform(request).andExpect(status().is(code))
                    .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.APPLICATION_JSON))
                    .andReturn().getResponse().getContentAsString());
            assertEquals(code, body.path("status").asInt());
            assertTrue(body.path("timestamp").isTextual());
            assertTrue(body.path("error").isTextual());
            assertTrue(body.path("message").isTextual());
        }
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "page,2147483648", "size,0", "size,101", "sort,title", "sort,relevance",
            "subject_id,0", "department_id,-1", "author_id,abc", "from,not-a-date", "from,2026-01-01T00:00:00Z",
            "from,2026-01-01T00:00:00.000000001"})
    void rejectsMalformedOrUnboundedParameters(String name, String value) throws Exception {
        mvc.perform(get(SEARCH).param(name, value).with(user(principal(2))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsOverlongQueriesReversedDatesAndUnknownAdminStatus() throws Exception {
        for (var request : new MockHttpServletRequestBuilder[]{get(SEARCH).param("q", "x".repeat(201)),
                get(SEARCH).param("from", "2026-02-01T00:00:00").param("to", "2026-01-01T00:00:00"),
                get(SEARCH).param("status", "UNKNOWN")}) {
            mvc.perform(request.with(user(principal(3)))).andExpect(status().isBadRequest());
        }
    }

    @Test
    void legacyRoutesStillReturnUnpagedArrays() throws Exception {
        for (String path : new String[]{"/api/v1/posts", "/api/v1/posts/subject/1",
                "/api/v1/posts/department/1", "/api/v1/posts/user/1"}) {
            JsonNode result = mapper.readTree(mvc.perform(get(path).with(user(principal(2))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertTrue(result.isArray());
        }
    }

    @Test
    void searchObservabilityCorrelatesOutcomesWithoutLoggingQueries(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        mvc.perform(get(SEARCH).param("q", "private-unlogged-query").header("X-Request-ID", "search-test-123")
                        .header("X-Private", "private-unlogged-header").with(user(principal(2))))
                .andExpect(status().isOk()).andExpect(header().string("X-Request-ID", "search-test-123"));
        var timer = meters.find("arca.post.search").tags("status", "200", "outcome", "success").timer();
        assertNotNull(timer);
        assertTrue(timer.count() > 0);
        assertTrue(output.getAll().contains("post_search outcome=success status=200 correlation_id=search-test-123"));
        assertFalse(output.getAll().contains("private-unlogged-query"));
        assertFalse(output.getAll().contains("private-unlogged-header"));
        mvc.perform(get(SEARCH).header("X-Request-ID", "invalid\nvalue"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("X-Request-ID", org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")));
        assertNotNull(meters.find("arca.post.search").tags("status", "401", "outcome", "rejected").timer());
    }

    private JsonNode page(MockHttpServletRequestBuilder request, long actor) throws Exception {
        return mapper.readTree(mvc.perform(request.with(user(principal(actor)))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private List<Long> ids(JsonNode page) {
        return StreamSupport.stream(page.path("content").spliterator(), false).map(post -> post.path("id").asLong()).toList();
    }

    private void insert(long id, int logical, int version, String title, String content, String status,
                        long author, long department, boolean latest, boolean deleted, String created) {
        jdbc.update("INSERT INTO posts(id,post_id,version,title,content,status,user_id,department_id,is_latest_version,is_deleted,created_at,updated_at) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", id, logical, version, title, content, status, author, department, latest, deleted, created, created);
    }

    private UserPrincipal principal(long id) {
        User actor = new User(); actor.setId(id); actor.setEmail("actor" + id + "@example.test");
        actor.addRole(id == 3 ? "ROLE_ADMIN" : "ROLE_USER");
        return new UserPrincipal(actor);
    }
}
