package com.popman.arca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popman.arca.entity.User;
import com.popman.arca.entity.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "logging.level.com.popman.arca.filter.PostDeletionObservationFilter=INFO")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class ProfileStatisticsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    @Autowired io.micrometer.core.instrument.MeterRegistry meters;

    @BeforeEach
    void seed() {
        for (String table : new String[]{"files", "votes", "vaults", "post_tags", "posts", "post_identities", "user_roles",
                "refresh_tokens", "department_subjects", "subject_aliases", "subjects", "departments", "school", "users"}) jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO users(id,email,first_name,last_name,is_deleted) VALUES "
                + "(1,'owner@example.test','Owner','Name',false),(2,'reader@example.test','Reader','Name',false),"
                + "(3,'admin@example.test','Admin','Name',false),(4,'deleted@example.test','Deleted','Name',true),"
                + "(5,'voter@example.test','Voter','Name',true),(6,'empty@example.test','Empty','Name',false)");
        jdbc.update("INSERT INTO user_roles(user_id,role) VALUES(1,'ROLE_USER'),(2,'ROLE_USER'),(3,'ROLE_ADMIN'),(4,'ROLE_USER'),(5,'ROLE_USER'),(6,'ROLE_USER')");
        jdbc.update("INSERT INTO school(id,name) VALUES(1,'School')");
        jdbc.update("INSERT INTO departments(id,name,school_id) VALUES(1,'Science',1),(2,'Mathematics',1)");
        jdbc.update("INSERT INTO subjects(id,code,name) VALUES(1,'PHY','Physics'),(2,'MAT','Math'),(3,'EMPTY','Empty')");
        jdbc.update("INSERT INTO subject_aliases(subject_id,alias) VALUES(1,'Original')");
        jdbc.update("INSERT INTO department_subjects(subject_id,department_id) VALUES(1,1),(2,2)");
        for (int id = 7; id <= 15; id++) jdbc.update("INSERT INTO post_identities(id,owner_id) VALUES(?,?)", id, id == 14 ? 2 : id == 15 ? 4 : 1);
        insertPost(10, 7, 1, "APPROVED", true, false, 1, "2026-01-02 10:00:00");
        insertPost(11, 7, 2, "PENDING_APPROVAL", true, false, 1, "2026-01-06 10:00:00");
        insertPost(12, 8, 1, "APPROVED", true, false, 1, "2026-01-03 11:30:00");
        insertPost(13, 9, 1, "PENDING_APPROVAL", true, false, 1, "2026-01-07 10:00:00");
        insertPost(14, 10, 1, "REJECTED", false, false, 1, "2026-01-08 10:00:00");
        insertPost(15, 11, 1, "SUPERSEDED", false, false, 1, "2026-01-09 10:00:00");
        insertPost(16, 12, 1, "APPROVED", false, false, 1, "2026-01-10 10:00:00");
        insertPost(17, 13, 1, "APPROVED", true, true, 1, "2026-01-11 10:00:00");
        insertPost(18, 14, 1, "APPROVED", true, false, 2, "2026-01-04 13:00:00");
        insertPost(19, 15, 1, "APPROVED", true, false, 4, "2026-01-05 14:00:00");
        jdbc.update("INSERT INTO post_tags(post_id,subject_id) VALUES(10,1),(10,2),(11,1),(12,1),(12,2),"
                + "(13,1),(14,1),(15,1),(16,1),(17,1),(18,1),(19,2)");
        jdbc.update("INSERT INTO votes(post_id,user_id,vote_type) VALUES(10,1,'UPVOTE'),(10,2,'UPVOTE'),(10,5,'UPVOTE'),(10,3,'DOWNVOTE'),"
                + "(12,2,'DOWNVOTE'),(12,3,'DOWNVOTE'),(11,2,'UPVOTE'),(13,2,'UPVOTE'),(14,2,'UPVOTE'),"
                + "(15,2,'UPVOTE'),(16,2,'UPVOTE'),(17,2,'UPVOTE'),(18,1,'UPVOTE'),(19,2,'DOWNVOTE')");
    }

    @Test
    void aggregateCountsExcludeEveryPrivateHistoricalOrDeletedVersionForAllRoles() throws Exception {
        for (long actor : new long[]{1, 2, 3}) {
            JsonNode stats = read("/api/v1/user/1/stats", actor);
            assertEquals(mapper.readTree("{\"approved_post_count\":2,\"upvotes_received\":3,\"downvotes_received\":3}"), stats);
            JsonNode subject = read("/api/v1/subject/1/stats", actor);
            assertEquals(3, subject.path("approved_post_count").asLong());
            assertEquals("2026-01-04T13:00:00", subject.path("last_approved_post_at").asText());
            assertEquals(Set.of("approved_post_count", "last_approved_post_at"), names(subject));
        }
    }

    @Test
    void emptyTargetsAndRetainedDeletedAuthorsAndVotersFollowExistingVisibility() throws Exception {
        assertEquals(mapper.readTree("{\"approved_post_count\":0,\"upvotes_received\":0,\"downvotes_received\":0}"), read("/api/v1/user/6/stats", 2));
        JsonNode empty = read("/api/v1/subject/3/stats", 2);
        assertEquals(0, empty.path("approved_post_count").asLong()); assertTrue(empty.path("last_approved_post_at").isNull());
        JsonNode retained = read("/api/v1/subject/2/stats", 2);
        assertEquals(3, retained.path("approved_post_count").asLong());
        assertEquals("2026-01-05T14:00:00", retained.path("last_approved_post_at").asText());
        assertEquals(3, read("/api/v1/user/1/stats", 2).path("upvotes_received").asLong());
        for (long actor : new long[]{1, 2, 3}) mvc.perform(get("/api/v1/user/4/stats").with(user(principal(actor)))).andExpect(status().isNotFound());
    }

    @Test
    void statsRequireAuthenticationAndValidateTargetsWithoutExposingModerationData() throws Exception {
        for (String path : new String[]{"/api/v1/user/1/stats", "/api/v1/subject/1/stats"}) mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for (String path : new String[]{"/api/v1/user/999/stats", "/api/v1/subject/999/stats"}) {
            mvc.perform(get(path).with(user(principal(3)))).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        }
        for (String path : new String[]{"/api/v1/user/0/stats", "/api/v1/subject/-1/stats", "/api/v1/user/abc/stats"}) {
            mvc.perform(get(path).with(user(principal(2)))).andExpect(status().isBadRequest());
        }
    }

    @Test
    void logicalDeletionRemovesAllItsEngagementFromStatsWithoutPurgingVotes() throws Exception {
        mvc.perform(delete("/api/v1/posts/11").with(user(principal(1)))).andExpect(status().isNoContent());
        assertEquals(mapper.readTree("{\"approved_post_count\":1,\"upvotes_received\":0,\"downvotes_received\":2}"), read("/api/v1/user/1/stats", 2));
        assertEquals(2, read("/api/v1/subject/1/stats", 2).path("approved_post_count").asLong());
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM votes WHERE post_id=10", Integer.class));
    }

    @Test
    void metadataIsAdditiveAndProfileEditsPreserveCreationAndGeneratedValueReadback() throws Exception {
        JsonNode before = read("/api/v1/user/1", 1);
        assertTrue(before.hasNonNull("created_at")); assertTrue(before.hasNonNull("updated_at"));
        var changed = mvc.perform(patch("/api/v1/user/me").with(user(principal(1))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"Changed\",\"created_at\":\"1900-01-01T00:00:00\",\"updated_at\":\"2099-01-01T00:00:00\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        JsonNode after = mapper.readTree(changed.getContentAsString());
        assertEquals(before.path("created_at"), after.path("created_at"));
        assertTrue(time(after, "updated_at").isAfter(time(before, "updated_at")));
        assertEquals(time(after, "updated_at"), jdbc.queryForObject("SELECT updated_at FROM users WHERE id=1", LocalDateTime.class));
        assertEquals("Owner", after.path("first_name").asText()); assertFalse(after.has("timestamps_approximate")); assertFalse(after.has("password"));
    }

    @Test
    void roleAndSubjectRelationOnlyChangesAdvanceParentTimestamps() throws Exception {
        JsonNode beforeUser = read("/api/v1/user/1", 2);
        mvc.perform(put("/api/v1/admin/users/1/promote").with(user(principal(3)))).andExpect(status().isOk());
        JsonNode afterUser = read("/api/v1/user/1", 2);
        assertEquals(beforeUser.path("created_at"), afterUser.path("created_at"));
        assertTrue(time(afterUser, "updated_at").isAfter(time(beforeUser, "updated_at")));
        JsonNode beforeSubject = read("/api/v1/subject/1", 2);
        JsonNode afterSubject = mapper.readTree(mvc.perform(put("/api/v1/subject/1").with(user(principal(3)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"PHY\",\"name\":\"Physics\",\"aliases\":[\"Changed\"],\"department_ids\":[2]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(beforeSubject.path("created_at"), afterSubject.path("created_at"));
        assertTrue(time(afterSubject, "updated_at").isAfter(time(beforeSubject, "updated_at")));
        assertEquals(time(afterSubject, "updated_at"), jdbc.queryForObject("SELECT updated_at FROM subjects WHERE id=1", LocalDateTime.class));
        assertEquals("Changed", afterSubject.path("aliases").get(0).asText());
    }

    @Test
    void databaseDefaultsAndExplicitMaintenanceUpdatesUseUtcEvenInNonUtcSession() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            String original = jdbc.queryForObject("SELECT @@session.time_zone", String.class);
            try {
                jdbc.update("SET time_zone=?", "+08:00");
                jdbc.update("INSERT INTO users(id,email,is_deleted) VALUES(100,'utc@example.test',false)");
                assertEquals(0, jdbc.queryForObject("SELECT ABS(TIMESTAMPDIFF(SECOND,created_at,UTC_TIMESTAMP(6))) FROM users WHERE id=100", Integer.class));
                LocalDateTime created = jdbc.queryForObject("SELECT created_at FROM users WHERE id=100", LocalDateTime.class);
                LocalDateTime updated = jdbc.queryForObject("SELECT updated_at FROM users WHERE id=100", LocalDateTime.class);
                jdbc.update("UPDATE users SET bio='SQL edit',updated_at=UTC_TIMESTAMP(6) WHERE id=100");
                assertEquals(created, jdbc.queryForObject("SELECT created_at FROM users WHERE id=100", LocalDateTime.class));
                assertTrue(jdbc.queryForObject("SELECT updated_at FROM users WHERE id=100", LocalDateTime.class).isAfter(updated));
                assertEquals(0, jdbc.queryForObject("SELECT ABS(TIMESTAMPDIFF(SECOND,updated_at,UTC_TIMESTAMP(6))) FROM users WHERE id=100", Integer.class));
            } finally { jdbc.update("SET time_zone=?", original); }
            tx.setRollbackOnly();
        });
    }

    @Test
    void newUserAndSubjectReceiveUtcMetadataAndIgnoreClientSuppliedTimes() throws Exception {
        java.time.Instant start = java.time.Instant.now().minusSeconds(1);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"email\":\"newperson@example.test\",\"password\":\"NewPassword1!\",\"first_name\":\"New\",\"last_name\":\"Person\",\"created_at\":\"1900-01-01T00:00:00Z\"}"))
                .andExpect(status().isCreated());
        long userId = jdbc.queryForObject("SELECT id FROM users WHERE email='newperson@example.test'", Long.class);
        JsonNode createdUser = read("/api/v1/user/" + userId, 2);
        assertTrue(java.time.Instant.parse(createdUser.path("created_at").asText()).isAfter(start));
        assertEquals(createdUser.path("created_at"), createdUser.path("updated_at"));
        assertFalse(jdbc.queryForObject("SELECT timestamps_approximate FROM users WHERE id=?", Boolean.class, userId));
        JsonNode createdSubject = mapper.readTree(mvc.perform(post("/api/v1/subject").with(user(principal(3)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"CHEM\",\"name\":\"Chemistry\",\"aliases\":[],\"department_ids\":[1],\"created_at\":\"1900-01-01T00:00:00Z\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(java.time.Instant.parse(createdSubject.path("created_at").asText()).isAfter(start));
        assertEquals(createdSubject.path("created_at"), createdSubject.path("updated_at"));
        assertFalse(jdbc.queryForObject("SELECT timestamps_approximate FROM subjects WHERE id=?", Boolean.class, createdSubject.path("id").asLong()));
        JsonNode summary = read("/api/v1/user/search?q=New", 2).get(0);
        assertTrue(summary.hasNonNull("created_at")); assertFalse(summary.has("password")); assertFalse(summary.has("email"));
    }

    @Test
    void legacyProvenanceSurvivesProfileMutationAndSoftDeleteUpdatesMetadata() throws Exception {
        jdbc.update("UPDATE users SET timestamps_approximate=true,updated_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY) WHERE id=1");
        JsonNode before = read("/api/v1/user/1", 2);
        mvc.perform(patch("/api/v1/user/me").with(user(principal(1))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"Changed\",\"timestamps_approximate\":false}"))
                .andExpect(status().isOk());
        assertTrue(jdbc.queryForObject("SELECT timestamps_approximate FROM users WHERE id=1", Boolean.class));
        LocalDateTime updated = jdbc.queryForObject("SELECT updated_at FROM users WHERE id=1", LocalDateTime.class);
        mvc.perform(delete("/api/v1/user/1").with(user(principal(3)))).andExpect(status().isOk());
        assertEquals(time(before, "created_at"), jdbc.queryForObject("SELECT created_at FROM users WHERE id=1", LocalDateTime.class));
        assertTrue(jdbc.queryForObject("SELECT updated_at FROM users WHERE id=1", LocalDateTime.class).isAfter(updated));
        mvc.perform(get("/api/v1/user/1/stats").with(user(principal(2)))).andExpect(status().isNotFound());
    }

    @Test
    void logicalCountDoesNotMultiplyAcrossLegacyDuplicateLatestFlags() throws Exception {
        jdbc.update("UPDATE posts SET status='APPROVED' WHERE id=11");
        assertEquals(mapper.readTree("{\"approved_post_count\":2,\"upvotes_received\":4,\"downvotes_received\":3}"), read("/api/v1/user/1/stats", 2));
        assertEquals(3, read("/api/v1/subject/1/stats", 2).path("approved_post_count").asLong());
    }

    @Test
    void aggregateSelectCountStaysTwoAsPostsVotesAndSubjectsGrow() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            try {
                assertSelects("/api/v1/user/1/stats", 2);
                assertSelects("/api/v1/subject/1/stats", 2);
                var identities = new ArrayList<Object[]>(); var posts = new ArrayList<Object[]>();
                var tags = new ArrayList<Object[]>(); var votes = new ArrayList<Object[]>();
                for (int i = 0; i < 1000; i++) {
                    long id = 1000 + i; identities.add(new Object[]{id}); posts.add(new Object[]{id, id});
                    tags.add(new Object[]{id, 1}); tags.add(new Object[]{id, 2}); votes.add(new Object[]{id});
                }
                jdbc.batchUpdate("INSERT INTO post_identities(id,owner_id) VALUES(?,1)", identities);
                jdbc.batchUpdate("INSERT INTO posts(id,post_id,version,title,content,status,user_id,department_id,is_latest_version,is_deleted,created_at,updated_at) "
                        + "VALUES(?,?,1,'Volume','Body','APPROVED',1,1,true,false,'2026-02-01','2026-02-01')", posts);
                jdbc.batchUpdate("INSERT INTO post_tags(post_id,subject_id) VALUES(?,?)", tags);
                jdbc.batchUpdate("INSERT INTO votes(post_id,user_id,vote_type) VALUES(?,2,'UPVOTE')", votes);
                assertSelects("/api/v1/user/1/stats", 2);
                assertSelects("/api/v1/subject/1/stats", 2);
                assertEquals(1002, read("/api/v1/user/1/stats", 2).path("approved_post_count").asLong());
            } catch (Exception e) { throw new RuntimeException(e); }
            tx.setRollbackOnly();
        });
    }

    @Test
    void statisticsObservationsAreCorrelatedAndContentFree(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        mvc.perform(get("/api/v1/user/1/stats").with(user(principal(2))).header("X-Request-ID", "profile-test-123")
                        .header("X-Private", "do-not-log-header"))
                .andExpect(status().isOk()).andExpect(header().string("X-Request-ID", "profile-test-123"));
        mvc.perform(get("/api/v1/subject/1/stats").with(user(principal(2)))).andExpect(status().isOk());
        assertNotNull(meters.find("arca.profile.user.stats").tags("status", "200", "outcome", "success").timer());
        assertNotNull(meters.find("arca.profile.subject.stats").tags("status", "200", "outcome", "success").timer());
        assertTrue(output.getAll().contains("user_stats outcome=success status=200 correlation_id=profile-test-123"));
        assertFalse(output.getAll().contains("owner@example.test")); assertFalse(output.getAll().contains("Private body"));
        assertFalse(output.getAll().contains("do-not-log-header"));
        mvc.perform(get("/api/v1/user/1/stats").header("X-Request-ID", "invalid\nvalue"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("X-Request-ID", org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")));
        assertNotNull(meters.find("arca.profile.user.stats").tags("status", "401", "outcome", "rejected").timer());
    }

    private void assertSelects(String path, int expected) throws Exception {
        long before = selects(); read(path, 2); long executed = selects() - before;
        assertEquals(expected, executed, path + " must use existence + aggregate only");
    }
    private long selects() { return Long.parseLong(jdbc.queryForMap("SHOW SESSION STATUS LIKE 'Com_select'").get("Value").toString()); }

    private void insertPost(long id, int logical, int version, String status, boolean latest, boolean deleted, long author, String approved) {
        jdbc.update("INSERT INTO posts(id,post_id,version,title,content,status,user_id,department_id,is_latest_version,is_deleted,created_at,updated_at) "
                + "VALUES(?,?,?,'Title','Private body',?,?,1,?,?,'2026-01-01',?)", id, logical, version, status, author, latest, deleted, approved);
    }
    private JsonNode read(String path, long actor) throws Exception {
        return mapper.readTree(mvc.perform(get(path).with(user(principal(actor)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private LocalDateTime time(JsonNode json, String name) { return LocalDateTime.ofInstant(java.time.Instant.parse(json.path(name).asText()), java.time.ZoneOffset.UTC); }
    private Set<String> names(JsonNode json) { var names = new HashSet<String>(); json.fieldNames().forEachRemaining(names::add); return names; }
    private UserPrincipal principal(long id) {
        User user = new User(); user.setId(id); user.setEmail("actor" + id + "@example.test"); user.addRole(id == 3 ? "ROLE_ADMIN" : "ROLE_USER"); return new UserPrincipal(user);
    }
}
