package com.popman.arca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popman.arca.entity.User;
import com.popman.arca.entity.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.homepage.announcements-enabled=true", "app.homepage.trending-enabled=true"})
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class HomepageIntegrationTest {
    static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    @Autowired TestClock clock;
    @Autowired com.popman.arca.service.AnnouncementService announcements;

    @TestConfiguration
    static class Configuration {
        @Bean @Primary TestClock homepageTestClock() { return new TestClock(); }
    }
    static class TestClock extends Clock {
        Instant time = NOW;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return time; }
    }
    @BeforeEach void seed() {
        clock.time = NOW;
        for (String table : new String[]{"announcements", "audit_events", "files", "votes", "vaults", "post_tags", "posts", "post_identities",
                "user_roles", "refresh_tokens", "department_subjects", "subject_aliases", "subjects", "departments", "school", "users"})
            jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO users(id,email,is_deleted) VALUES(1,'owner@example.test',false),(2,'other@example.test',false),(3,'admin@example.test',false)");
        jdbc.update("INSERT INTO user_roles(user_id,role) VALUES(1,'ROLE_USER'),(2,'ROLE_USER'),(3,'ROLE_ADMIN')");
        jdbc.update("INSERT INTO school(id,name) VALUES(1,'School')"); jdbc.update("INSERT INTO departments(id,name,school_id) VALUES(1,'Department',1)");
    }

    @Test void announcementsRespectSchedulePriorityAndAdminLifecycle() throws Exception {
        long first = create(Map.of("title", "Low", "body", "Plain text", "active", true, "priority", 1)).path("id").asLong();
        long high = create(Map.of("title", "High", "url", "https://example.test/news", "active", true, "priority", 10,
                "starts_at", NOW.toString(), "ends_at", NOW.plusSeconds(3600).toString())).path("id").asLong();
        create(Map.of("title", "Future", "body", "Later", "active", true, "starts_at", NOW.plusSeconds(1).toString()));
        create(Map.of("title", "Expired", "body", "Old", "active", true, "ends_at", NOW.toString()));
        create(Map.of("title", "Inactive", "body", "Hidden"));
        var active = read("/api/v1/announcements/active", 1);
        assertEquals(2, active.size()); assertEquals(high, active.get(0).path("id").asLong());
        assertFalse(active.get(0).has("active")); assertFalse(active.get(0).has("created_by"));
        assertEquals(5, read("/api/v1/admin/announcements", 3).path("total_elements").asLong());
        mvc.perform(put("/api/v1/admin/announcements/" + first).with(user(actor(3))).contentType("application/json")
                .content("{\"title\":\"Updated\",\"body\":\"Body\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.updated_by").value(3));
        mvc.perform(delete("/api/v1/admin/announcements/" + high).with(user(actor(3)))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/admin/announcements/" + high).with(user(actor(3)))).andExpect(status().isNoContent());
        assertEquals(0, read("/api/v1/announcements/active", 1).size());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action='ANNOUNCEMENT_DELETED' AND target_id=?", Integer.class, high));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action='ANNOUNCEMENT_UPDATED' AND target_id=?", Integer.class, first));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action='ANNOUNCEMENT_CREATED' AND actor_id=3", Integer.class));
        mvc.perform(get("/api/v1/admin/announcements/" + high).with(user(actor(3)))).andExpect(status().isNotFound());
    }

    @Test void announcementValidationAuthorizationAndRollbackLeaveNoPartialRows() throws Exception {
        mvc.perform(get("/api/v1/announcements/active")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/announcements").with(user(actor(1))).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        for (Map<String, Object> body : List.<Map<String, Object>>of(Map.of("title", "Missing content"), Map.of("title", "Bad", "url", "javascript:alert(1)"),
                Map.of("title", "Bad", "url", "https://user:pass@example.test/"), Map.of("title", "Bad", "body", "text", "priority", -1),
                Map.of("title", "Bad", "body", "text", "starts_at", NOW.toString(), "ends_at", NOW.toString()),
                Map.of("title", "x".repeat(129), "body", "text"), Map.of("title", "Bad", "body", "x".repeat(4097)),
                Map.of("title", "Bad", "body", "text", "starts_at", "2026-11-17T12:00:00.000000001Z"))) {
            mvc.perform(post("/api/v1/admin/announcements").with(user(actor(3))).contentType("application/json").content(mapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM announcements", Integer.class));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            try { create(Map.of("title", "Rollback", "body", "Text")); } catch (Exception e) { throw new RuntimeException(e); }
            tx.setRollbackOnly();
        });
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM announcements", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action='ANNOUNCEMENT_CREATED'", Integer.class));
    }

    @Test void malformedUnicodeCannotSilentlyCorruptAnnouncementText() throws Exception {
        mvc.perform(post("/api/v1/admin/announcements").with(user(actor(3))).contentType("application/json")
                .content("{\"title\":\"Invalid\",\"body\":\"\\uD800\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM announcements", Integer.class));
    }

    @Test void trendingRanksRecentHeldVotesWithAgeDecayAndStableTiesWithoutVisibilityLeaks() throws Exception {
        postRow(10, 10, 1, true, "APPROVED", false, NOW.minusSeconds(3600));
        postRow(11, 11, 1, true, "APPROVED", false, NOW.minusSeconds(3600));
        postRow(12, 12, 1, true, "APPROVED", false, NOW.minusSeconds(7200));
        postRow(13, 13, 1, true, "APPROVED", false, null);
        postRow(14, 14, 1, true, "PENDING_APPROVAL", false, NOW.minusSeconds(100));
        postRow(15, 15, 1, true, "APPROVED", true, NOW.minusSeconds(100));
        postRow(16, 16, 1, false, "APPROVED", false, NOW.minusSeconds(100));
        postRow(17, 17, 1, true, "APPROVED", false, NOW.minus(Duration.ofDays(8)));
        postRow(18, 18, 1, true, "APPROVED", false, NOW.plusSeconds(1));
        postRow(19, 19, 1, true, "REJECTED", false, NOW.minusSeconds(1));
        jdbc.update("INSERT INTO votes(post_id,user_id,vote_type,changed_at) VALUES(12,1,'UPVOTE',?),(12,2,'UPVOTE',?),(11,1,'DOWNVOTE',?),(10,1,'UPVOTE',NULL)", utc(NOW.minusSeconds(10)), utc(NOW.minusSeconds(20)), utc(NOW.minusSeconds(10)));
        jdbc.update("UPDATE users SET is_deleted=true WHERE id=2");
        var response = read("/api/v1/posts/trending", 1);
        assertEquals(NOW.toString(), response.path("calculated_at").asText());
        var content = response.path("content"); assertEquals(3, content.size());
        assertEquals(12, content.get(0).path("id").asLong());
        assertEquals(10, content.get(1).path("id").asLong());
        assertEquals(11, content.get(2).path("id").asLong());
        assertEquals(2, content.get(0).path("recent_upvotes").asLong()); assertFalse(content.get(0).has("score"));
        jdbc.update("DELETE FROM votes");
        content = read("/api/v1/posts/trending", 1).path("content");
        assertEquals(11, content.get(0).path("id").asLong()); assertEquals(10, content.get(1).path("id").asLong());
        mvc.perform(get("/api/v1/posts/trending").with(user(actor(1))).param("limit", "51")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/posts/trending").with(user(actor(1))).param("window", "all")).andExpect(status().isBadRequest());
    }

    @Test void trendingDeduplicatesLogicalPostsAndVoteSwitchToggleAndRemovalStayCompatible() throws Exception {
        clock.time = Instant.now().plusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        postRow(10, 10, 1, true, "APPROVED", false, clock.time.minusSeconds(3600));
        postRow(11, 10, 2, true, "APPROVED", false, clock.time.minusSeconds(1800));
        assertEquals(1, read("/api/v1/posts/trending", 1).path("content").size());
        vote(11, "UPVOTE", 200);
        assertNotNull(jdbc.queryForObject("SELECT changed_at FROM votes WHERE post_id=11", LocalDateTime.class));
        assertEquals(1, read("/api/v1/posts/trending", 1).at("/content/0/recent_upvotes").asLong());
        vote(11, "DOWNVOTE", 200);
        assertEquals(1, read("/api/v1/posts/trending", 1).at("/content/0/recent_downvotes").asLong());
        vote(11, "DOWNVOTE", 204); assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM votes", Integer.class));
        vote(11, "UPVOTE", 200); mvc.perform(delete("/api/v1/votes/11").with(user(actor(1)))).andExpect(status().isNoContent());
        assertEquals(0, read("/api/v1/posts/trending", 1).at("/content/0/recent_upvotes").asLong());
        jdbc.update("UPDATE posts SET approved_at=NULL WHERE id=11");
        assertEquals(0, read("/api/v1/posts/trending", 1).path("content").size(), "Unknown newest approval must not resurrect an older version");
    }

    @Test void recentVoteWindowHasInclusiveStartExclusiveEndAndBoundedChoices() throws Exception {
        postRow(10, 10, 1, true, "APPROVED", false, NOW.minusSeconds(3600));
        postRow(11, 11, 1, true, "APPROVED", false, NOW.minus(Duration.ofDays(2)));
        jdbc.update("INSERT INTO votes(post_id,user_id,vote_type,changed_at) VALUES(10,1,'UPVOTE',?),(10,2,'UPVOTE',?),(10,3,'UPVOTE',?)",
                utc(NOW.minus(Duration.ofDays(7))), utc(NOW), utc(NOW.minus(Duration.ofDays(7)).minusNanos(1000)));
        assertEquals(1, read("/api/v1/posts/trending", 1).at("/content/0/recent_upvotes").asLong());
        mvc.perform(get("/api/v1/posts/trending").with(user(actor(1))).param("window", "24h"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.content[0].recent_upvotes").value(0));
        mvc.perform(get("/api/v1/posts/trending").with(user(actor(1))).param("window", "30d").param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test void auditFailureRollsBackAnnouncementReplacementAndPublicReadsNeverFetchLinks() throws Exception {
        var hits = new java.util.concurrent.atomic.AtomicInteger();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> { hits.incrementAndGet(); exchange.sendResponseHeaders(204,-1); exchange.close(); }); server.start();
        try {
            long id = create(Map.of("title", "Original", "body", "<script>plain text</script>", "active", true,
                    "url", "http://127.0.0.1:" + server.getAddress().getPort() + "/untrusted")).path("id").asLong();
            assertEquals("<script>plain text</script>", read("/api/v1/announcements/active", 1).get(0).path("body").asText());
            assertEquals(0, hits.get());
            jdbc.execute("RENAME TABLE audit_events TO audit_events_unavailable");
            try {
                mvc.perform(put("/api/v1/admin/announcements/" + id).with(user(actor(3))).contentType("application/json")
                        .content("{\"title\":\"Must roll back\",\"body\":\"Text\"}")).andExpect(status().isInternalServerError());
                assertEquals("Original", jdbc.queryForObject("SELECT title FROM announcements WHERE id=?", String.class, id));
            } finally { jdbc.execute("RENAME TABLE audit_events_unavailable TO audit_events"); }
        } finally { server.stop(0); }
    }

    @Test @org.springframework.security.test.context.support.WithMockUser(roles = "USER")
    void adminAnnouncementMethodsDenyDirectNonAdminInvocation() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> announcements.delete(1));
    }

    @Test void concurrentAnnouncementDeleteDoesNotDuplicateAudit() throws Exception {
        long id = create(Map.of("title", "Delete once", "body", "Text", "active", true)).path("id").asLong();
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> deletion = () -> {
                start.await();
                return mvc.perform(delete("/api/v1/admin/announcements/" + id).with(user(actor(3)))).andReturn().getResponse().getStatus();
            };
            var first = pool.submit(deletion); var second = pool.submit(deletion); start.countDown();
            assertEquals(204, first.get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(204, second.get(10, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action='ANNOUNCEMENT_DELETED' AND target_id=?", Integer.class, id));
        assertEquals(0, read("/api/v1/announcements/active", 1).size());
    }

    private void vote(long row, String type, int expected) throws Exception {
        mvc.perform(post("/api/v1/votes").with(user(actor(1))).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("post_id", row, "vote_type", type)))).andExpect(status().is(expected));
    }
    private JsonNode create(Map<String, Object> body) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/admin/announcements").with(user(actor(3))).contentType("application/json")
                .content(mapper.writeValueAsString(body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode read(String path, long who) throws Exception {
        return mapper.readTree(mvc.perform(get(path).with(user(actor(who)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private void postRow(long id, int logical, int version, boolean latest, String status, boolean deleted, Instant approved) {
        if (version == 1) jdbc.update("INSERT INTO post_identities(id,owner_id) VALUES(?,1)", logical);
        jdbc.update("INSERT INTO posts(id,post_id,version,title,content,status,user_id,department_id,is_latest_version,is_deleted,created_at,updated_at,approved_at) VALUES(?,?,?,'Title','Body',?,1,1,?,?,'2026-01-01','2026-01-01',?)", id, logical, version, status, latest, deleted, utc(approved));
    }
    static LocalDateTime utc(Instant instant) { return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC); }
    static UserPrincipal actor(long id) {
        User account = new User(); account.setId(id); account.setEmail(id + "@example.test"); account.addRole(id == 3 ? "ROLE_ADMIN" : "ROLE_USER");
        return new UserPrincipal(account);
    }
}
