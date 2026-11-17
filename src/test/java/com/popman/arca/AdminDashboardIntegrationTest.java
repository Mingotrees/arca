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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class AdminDashboardIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    @Autowired com.popman.arca.service.AdminDashboardService dashboardService;

    @BeforeEach
    void seed() {
        for (String table : new String[]{"files", "votes", "vaults", "post_tags", "posts", "post_identities",
                "user_roles", "refresh_tokens", "department_subjects", "subject_aliases", "subjects", "departments", "school", "users"})
            jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO users(id,email,is_deleted) VALUES(1,'owner@example.test',false),(2,'deleted@example.test',true),(3,'admin@example.test',false),(4,'legacy@example.test',null)");
        jdbc.update("INSERT INTO user_roles(user_id,role) VALUES(1,'ROLE_USER'),(2,'ROLE_USER'),(3,'ROLE_ADMIN'),(3,'ROLE_USER'),(4,'ROLE_USER')");
        jdbc.update("INSERT INTO school(id,name) VALUES(1,'School')");
        jdbc.update("INSERT INTO departments(id,name,school_id) VALUES(1,'Department',1)");
    }

    @Test
    void onlyAdminsReceiveDashboardAndEmptyCountsAreZero() throws Exception {
        mvc.perform(get("/api/v1/admin/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/dashboard").with(user(actor(1)))).andExpect(status().isForbidden());
        JsonNode result = dashboard(null, null);
        assertEquals(3, result.at("/users/active").asLong());
        assertEquals(1, result.at("/users/deleted").asLong());
        assertEquals(0, result.at("/posts/active").asLong());
        assertEquals(0, result.at("/posts/approved_versions_in_period").asLong());
        assertEquals(0, result.at("/engagement/attachment_bytes").asLong());
        assertEquals("UTC", result.path("timezone").asText());
        assertEquals(java.time.Duration.ofDays(90), java.time.Duration.between(
                Instant.parse(result.path("from").asText()), Instant.parse(result.path("to").asText())));
        assertTrue(result.hasNonNull("calculated_at"));
    }

    @Test
    void anonymousDirectServiceInvocationIsDenied() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        assertThrows(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,
                () -> dashboardService.dashboard(null, null));
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(roles = "USER")
    void regularUserDirectServiceInvocationIsDenied() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> dashboardService.dashboard(null, null));
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(roles = "ADMIN")
    void adminDirectServiceInvocationSucceeds() {
        assertEquals(3, dashboardService.dashboard(null, null).users().active());
    }

    @Test
    void aggregatesDoNotMultiplyVersionsOrRolesAndPeriodApprovalsIncludeDeletedHistory() throws Exception {
        postRow(10, 1, 1, "SUPERSEDED", false, false, "2026-01-01", "2026-02-01");
        postRow(11, 1, 2, "APPROVED", true, false, "2026-02-02", "2026-02-03");
        postRow(20, 2, 1, "APPROVED", false, false, "2026-02-01", null);
        postRow(21, 2, 2, "REJECTED", false, false, "2026-02-04", null);
        postRow(30, 3, 1, "PENDING_APPROVAL", true, false, "2026-02-02", null);
        postRow(40, 4, 1, "APPROVED", true, true, "2026-02-01", "2026-02-02");
        postRow(50, 5, 1, "APPROVED", true, false, "2026-03-01", "2026-03-01");
        jdbc.update("INSERT INTO files(post_id,user_id,file_size) VALUES(11,1,3000000000),(11,1,NULL),(11,1,-1),(10,1,99),(40,1,88)");
        jdbc.update("INSERT INTO votes(post_id,user_id,vote_type) VALUES(11,1,'UPVOTE'),(11,2,'DOWNVOTE'),(10,1,'UPVOTE'),(40,1,'UPVOTE')");
        jdbc.update("INSERT INTO vaults(post_id,user_id) VALUES(11,1),(11,2),(10,1),(40,1)");
        JsonNode result = dashboard("2026-02-01T01:00:00+01:00", "2026-03-01T00:00:00Z");
        assertEquals("2026-02-01T00:00:00Z", result.path("from").asText());
        assertEquals(4, result.at("/posts/active").asLong());
        assertEquals(1, result.at("/posts/deleted").asLong());
        assertEquals(2, result.at("/posts/by_status/APPROVED").asLong());
        assertEquals(1, result.at("/posts/by_status/REJECTED").asLong());
        assertEquals(1, result.at("/moderation/pending").asLong());
        assertEquals(3, result.at("/posts/created_in_period").asLong());
        assertEquals(3, result.at("/posts/approved_versions_in_period").asLong());
        assertEquals(1, result.at("/posts/legacy_approved_versions_without_date").asLong());
        assertEquals(3, result.at("/users/by_role/ROLE_USER").asLong());
        assertEquals(1, result.at("/users/by_role/ROLE_ADMIN").asLong());
        assertEquals(3, result.at("/engagement/attachments").asLong());
        assertEquals(3000000000L, result.at("/engagement/attachment_bytes").asLong());
        assertEquals(2, result.at("/engagement/attachments_without_known_size").asLong());
        assertEquals(1, result.at("/engagement/upvotes").asLong());
        assertEquals(1, result.at("/engagement/downvotes").asLong());
        assertEquals(2, result.at("/engagement/vault_saves").asLong());
        assertFalse(result.toString().contains("example.test"));
    }

    @Test
    void validatesWindowsAndSupportsSingleBounds() throws Exception {
        for (String[] range : new String[][]{{"2026-03-01T00:00:00Z", "2026-03-01T00:00:00Z"},
                {"2026-03-02T00:00:00Z", "2026-03-01T00:00:00Z"}, {"2026-01-01T00:00:00Z", "2026-05-01T00:00:00Z"},
                {"garbage", "2026-03-01T00:00:00Z"}, {"0999-01-01T00:00:00Z", "0999-02-01T00:00:00Z"},
                {"2026-02-01T00:00:00.000000001Z", "2026-03-01T00:00:00Z"}}) {
            mvc.perform(get("/api/v1/admin/dashboard").with(user(actor(3))).param("from", range[0]).param("to", range[1]))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        }
        JsonNode result = dashboard(null, "2026-03-01T00:00:00Z");
        assertEquals("2025-12-01T00:00:00Z", result.path("from").asText());
        String recent = Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MICROS).toString();
        assertEquals(recent, dashboard(recent, null).path("from").asText());
    }

    @Test
    void microsecondBoundariesAndOffsetChangesUseElapsedUtcTime() throws Exception {
        postRow(10, 1, 1, "APPROVED", true, false, "2026-02-01", "2026-02-01");
        postRow(20, 2, 1, "SUPERSEDED", false, false, "2026-01-01", "2026-03-01");
        jdbc.update("UPDATE posts SET approved_at='2026-02-28 23:59:59.999999' WHERE id=20");
        postRow(30, 3, 1, "APPROVED", true, false, "2026-03-01", "2026-03-01");
        JsonNode result = dashboard("2026-02-01T00:00:00Z", "2026-03-01T00:00:00Z");
        assertEquals(2, result.at("/posts/approved_versions_in_period").asLong());
        assertEquals(1, result.at("/posts/created_in_period").asLong());
        JsonNode dst = dashboard("2026-03-08T01:00:00-05:00", "2026-03-08T04:00:00-04:00");
        assertEquals("2026-03-08T06:00:00Z", dst.path("from").asText());
        assertEquals("2026-03-08T08:00:00Z", dst.path("to").asText());
        mvc.perform(get("/api/v1/admin/dashboard").with(user(actor(3))).param("to", "-999999999-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approvalTimestampIsUtcImmutableAcrossVersionsAndDeletionAndRollsBack() throws Exception {
        long row = create();
        assertNull(jdbc.queryForObject("SELECT approved_at FROM posts WHERE id=?", LocalDateTime.class, row));
        Instant before = Instant.now().minusSeconds(1);
        approve(row, true);
        LocalDateTime first = jdbc.queryForObject("SELECT approved_at FROM posts WHERE id=?", LocalDateTime.class, row);
        assertNotNull(first);
        assertTrue(first.toInstant(java.time.ZoneOffset.UTC).isAfter(before));
        assertTrue(first.toInstant(java.time.ZoneOffset.UTC).isBefore(Instant.now().plusSeconds(1)));
        long next = mapper.readTree(mvc.perform(put("/api/v1/posts/" + row).with(user(actor(1)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Revision\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asLong();
        assertNull(jdbc.queryForObject("SELECT approved_at FROM posts WHERE id=?", LocalDateTime.class, next));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            try { approve(next, true); } catch (Exception e) { throw new RuntimeException(e); }
            tx.setRollbackOnly();
        });
        assertNull(jdbc.queryForObject("SELECT approved_at FROM posts WHERE id=?", LocalDateTime.class, next));
        approve(next, true);
        assertEquals(first, jdbc.queryForObject("SELECT approved_at FROM posts WHERE id=?", LocalDateTime.class, row));
        mvc.perform(delete("/api/v1/posts/" + next).with(user(actor(1)))).andExpect(status().isNoContent());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE approved_at IS NOT NULL AND is_deleted=true", Integer.class));
        long rejected = create(); approve(rejected, false);
        assertNull(jdbc.queryForObject("SELECT approved_at FROM posts WHERE id=?", LocalDateTime.class, rejected));
    }

    private JsonNode dashboard(String from, String to) throws Exception {
        var request = get("/api/v1/admin/dashboard").with(user(actor(3)));
        if (from != null) request.param("from", from);
        if (to != null) request.param("to", to);
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private void postRow(long id, int logical, int version, String status, boolean latest, boolean deleted, String created, String approved) {
        if (version == 1) jdbc.update("INSERT INTO post_identities(id,owner_id) VALUES(?,1)", logical);
        jdbc.update("INSERT INTO posts(id,post_id,version,title,content,status,user_id,department_id,is_latest_version,is_deleted,created_at,updated_at,approved_at) VALUES(?,?,?,'Post','Body',?,1,1,?,?,?, ?,?)",
                id, logical, version, status, latest, deleted, created, created, approved);
    }
    private long create() throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/posts").with(user(actor(1))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"New\",\"content\":\"Body\",\"department_id\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();
    }
    private void approve(long row, boolean approved) throws Exception {
        mvc.perform(post("/api/v1/posts/" + row + "/approve").with(user(actor(3))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"approved\":" + approved + "}")).andExpect(status().isOk());
    }
    private UserPrincipal actor(long id) {
        User user = new User(); user.setId(id); user.setEmail(id + "@example.test"); user.addRole(id == 3 ? "ROLE_ADMIN" : "ROLE_USER");
        return new UserPrincipal(user);
    }
}
