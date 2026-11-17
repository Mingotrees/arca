package com.popman.arca;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.popman.arca.entity.User;
import com.popman.arca.entity.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class AuditEventsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    @Autowired com.popman.arca.service.UserService users;
    @Autowired com.popman.arca.service.SchoolService schools;
    @Autowired com.popman.arca.service.DepartmentService departments;
    @Autowired com.popman.arca.service.SubjectService subjects;
    @Autowired com.popman.arca.service.BannedEmailService banned;
    @Autowired com.popman.arca.service.AuthSessionService sessions;
    @Autowired com.popman.arca.service.audit.AuditService audit;
    @Autowired com.popman.arca.service.audit.SecurityAuditWriter securityAudit;
    @Autowired com.popman.arca.service.AuditQueryService queries;
    @org.springframework.boot.test.mock.mockito.SpyBean com.popman.arca.repository.UserRepository userRepository;
    @org.junit.jupiter.api.io.TempDir static java.nio.file.Path media;
    @org.springframework.test.context.DynamicPropertySource
    static void mediaDirectory(org.springframework.test.context.DynamicPropertyRegistry properties) {
        properties.add("file.upload-dir", () -> media.toString());
    }

    @Test
    void invalidJwtDenialIsObservedWithoutRecordingBearerToken() throws Exception {
        jdbc.update("DELETE FROM audit_events");
        mvc.perform(get("/api/v1/admin/audit-events").header("Authorization", "Bearer secret-invalid-token"))
                .andExpect(status().isUnauthorized());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action='ACCESS_DENIED' AND actor_id IS NULL AND target_id IS NULL", Integer.class));
    }

    @Test
    void encodedMvcPathsKeepSecurityClassificationAndCorrelation() throws Exception {
        jdbc.update("DELETE FROM audit_events");
        mvc.perform(post(java.net.URI.create("/api/%761/auth/password-recovery")).contentType("application/json")
                .content("{\"email\":\"private@example.test\"}").header("X-Request-ID", "encoded-recovery"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("X-Request-ID", "encoded-recovery"));
        mvc.perform(post(java.net.URI.create("/api/v1/auth/%6cogin")).contentType("application/json")
                .content("{\"email\":\"missing@example.test\",\"password\":\"Secret1\"}").header("X-Request-ID", "encoded-login"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(java.net.URI.create("/api/%761/admin/audit-events")).with(user(actor(false))).header("X-Request-ID", "encoded-denial"))
                .andExpect(status().isForbidden()).andExpect(header().string("X-Request-ID", "encoded-denial"));
        assertEquals("PASSWORD_RECOVERY", jdbc.queryForObject("SELECT action FROM audit_events WHERE correlation_id='encoded-recovery'", String.class));
        assertEquals("LOGIN", jdbc.queryForObject("SELECT action FROM audit_events WHERE correlation_id='encoded-login'", String.class));
        assertEquals("ACCESS_DENIED", jdbc.queryForObject("SELECT action FROM audit_events WHERE correlation_id='encoded-denial'", String.class));
    }

    @Test
    void concurrentUserDeletionCommitsOnlyOneTransitionAndOneEvent() throws Exception {
        jdbc.update("DELETE FROM audit_events");
        jdbc.update("INSERT INTO users(email,is_deleted) VALUES('concurrent-audit-delete@example.test',false)");
        long id = jdbc.queryForObject("SELECT id FROM users WHERE email='concurrent-audit-delete@example.test'", Long.class);
        var bothExistenceChecks = new java.util.concurrent.CyclicBarrier(2);
        var realRepository = org.mockito.Mockito.mockingDetails(userRepository).getMockCreationSettings().getDefaultAnswer();
        org.mockito.Mockito.doAnswer(call -> {
            Object result = realRepository.answer(call);
            bothExistenceChecks.await(5, java.util.concurrent.TimeUnit.SECONDS);
            return result;
        }).when(userRepository).existsById(id);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Boolean> deletion = () -> {
                start.await();
                try { users.deleteUserv1(id); return true; }
                catch (RuntimeException expectedForSecondDelete) { return false; }
            };
            var first = pool.submit(deletion); var second = pool.submit(deletion); start.countDown();
            int successes = (first.get(15, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(15, java.util.concurrent.TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes, "Only one request may perform the active-to-deleted transition");
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action='USER_DELETED' AND target_id=?", Integer.class, id));
            assertTrue(jdbc.queryForObject("SELECT is_deleted FROM users WHERE id=?", Boolean.class, id));
        } finally {
            org.mockito.Mockito.doAnswer(realRepository).when(userRepository).existsById(id);
            jdbc.update("DELETE FROM users WHERE id=?", id);
        }
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(roles = "USER")
    void serviceQueryDeniesNonAdminAndSecurityEventsSurviveOuterRollback() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> queries.query(null, null, null, null, null, null, null, null, null));
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                () -> audit.success(com.popman.arca.service.audit.AuditAction.USER_DELETED, 1L));
        jdbc.update("DELETE FROM audit_events");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            audit.success(com.popman.arca.service.audit.AuditAction.USER_DELETED, 1L);
            securityAudit.record(com.popman.arca.service.audit.AuditAction.ACCESS_DENIED, "DENIED", null, "independent");
            tx.setRollbackOnly();
        });
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class));
        assertEquals("independent", jdbc.queryForObject("SELECT correlation_id FROM audit_events", String.class));
    }

    @Test
    void accountTaxonomyMediaAndSessionMutationsEmitRedactedEvents() throws Exception {
        jdbc.update("DELETE FROM audit_events");
        var account = new User(); account.setEmail("audit-matrix@example.test"); account.setFirstName("Audit"); account.setPassword("Password123!");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(actor(true), "", actor(true).getAuthorities()));
        try {
            users.createAdminV1(account);
            long id = account.getId();
            users.addRoleToUserV1(id, "ROLE_REVIEWER"); users.removeRoleFromUserV1(id, "ROLE_REVIEWER");
            sessions.login(account.getEmail(), "Password123!");
            banned.banV1("do-not-store@example.test", "do-not-store-reason"); banned.unbanV1("do-not-store@example.test");
            var school = new com.popman.arca.dto.v1.school.SchoolRequest(); school.setName("Audit School");
            long schoolId = schools.addSchoolV1(school).getId(); schools.editSchoolV1(schoolId, school);
            var department = new com.popman.arca.dto.v1.department.DepartmentRequest(); department.setName("Audit Dept"); department.setSchoolId(schoolId);
            long departmentId = departments.createDepartmentV1(department).getId(); departments.updateDepartmentV1(departmentId, department);
            var subject = new com.popman.arca.dto.v1.subject.SubjectRequest(); subject.setCode("AUDIT"); subject.setName("Audit Subject");
            subject.setAliases(new java.util.ArrayList<>()); subject.setDepartmentIds(java.util.List.of(departmentId));
            long subjectId = subjects.createSubjectV1(subject).getId(); subjects.updateSubjectV1(subjectId, subject);
            subjects.deleteSubjectV1(subjectId); departments.deleteDepartmentV1(departmentId); schools.deleteSchoolV1(schoolId);
            var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
            var bytes = new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(image, "png", bytes);
            var upload = new org.springframework.mock.web.MockMultipartFile("file", "secret-filename.png", "image/png", bytes.toByteArray());
            users.updateProfilePictureV1(id, upload); users.updateCoverPictureV1(id, upload); users.deleteCoverPictureV1(id); users.deleteCoverPictureV1(id);
            users.deleteUserv1(id);
            for (String action : new String[]{"ADMIN_CREATED", "ROLE_ADDED", "ROLE_REMOVED", "LOGIN", "EMAIL_BANNED", "EMAIL_UNBANNED",
                    "SCHOOL_CREATED", "SCHOOL_UPDATED", "SCHOOL_DELETED", "DEPARTMENT_CREATED", "DEPARTMENT_UPDATED", "DEPARTMENT_DELETED",
                    "SUBJECT_CREATED", "SUBJECT_UPDATED", "SUBJECT_DELETED", "AVATAR_CHANGED", "COVER_CHANGED", "COVER_REMOVED", "USER_DELETED"})
                assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action=? AND outcome='SUCCESS'", Integer.class, action), action);
            String recorded = jdbc.queryForList("SELECT * FROM audit_events").toString();
            for (String secret : new String[]{"Password123!", "do-not-store", "audit-matrix@", "secret-filename", "uploads/"}) assertFalse(recorded.contains(secret));
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    @Test
    void deniedRequestsAndFailedLoginAreRedactedAndAuditedOnce() throws Exception {
        jdbc.update("DELETE FROM audit_events");
        mvc.perform(get("/api/v1/admin/audit-events").with(user(actor(false))).header("X-Request-ID", "denied-123"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"untrusted-secret@example.test\",\"password\":\"NeverStoreMe1\"}"))
                .andExpect(status().isUnauthorized());
        var rows = jdbc.queryForList("SELECT * FROM audit_events ORDER BY id");
        assertEquals(2, rows.size());
        assertEquals("ACCESS_DENIED", rows.get(0).get("action"));
        assertEquals(999L, ((Number) rows.get(0).get("actor_id")).longValue());
        assertEquals("denied-123", rows.get(0).get("correlation_id"));
        assertEquals("LOGIN", rows.get(1).get("action"));
        assertNull(rows.get(1).get("actor_id")); assertNull(rows.get(1).get("target_id"));
        assertFalse(rows.toString().contains("NeverStoreMe1"));
        assertFalse(rows.toString().contains("untrusted-secret"));
    }

    @Test
    void auditOutageRollsBackRoleMutationButCannotChangeSecurityFailureResponse() throws Exception {
        jdbc.update("INSERT INTO users(email,is_deleted) VALUES('outage-target@example.test',false)");
        long target = jdbc.queryForObject("SELECT id FROM users WHERE email='outage-target@example.test'", Long.class);
        jdbc.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ROLE_USER')", target);
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", bytes);
        var upload = new org.springframework.mock.web.MockMultipartFile("file", "cover.png", "image/png", bytes.toByteArray());
        String oldCover = users.updateCoverPictureV1(target, upload);
        jdbc.execute("RENAME TABLE audit_events TO audit_events_unavailable");
        try {
            mvc.perform(put("/api/v1/admin/users/" + target + "/promote").with(user(actor(true)))).andExpect(status().isInternalServerError());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM user_roles WHERE user_id=? AND role='ROLE_ADMIN'", Integer.class, target));
            assertThrows(RuntimeException.class, () -> users.updateCoverPictureV1(target, upload));
            assertEquals(oldCover, jdbc.queryForObject("SELECT cover_picture FROM users WHERE id=?", String.class, target));
            assertTrue(java.nio.file.Files.exists(media.resolve(oldCover)));
            try (var files = java.nio.file.Files.list(media.resolve("cover-pictures"))) { assertEquals(1, files.count()); }
            mvc.perform(get("/api/v1/admin/audit-events").with(user(actor(false)))).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"email\":\"missing@example.test\",\"password\":\"Secret1\"}"))
                    .andExpect(status().isUnauthorized());
        } finally {
            jdbc.execute("RENAME TABLE audit_events_unavailable TO audit_events");
            jdbc.update("DELETE FROM user_roles WHERE user_id=?", target); jdbc.update("DELETE FROM users WHERE id=?", target);
        }
    }

    @Test
    void adminQueryIsBoundedFilteredStableAndHasNoMutationRoute() throws Exception {
        jdbc.update("DELETE FROM audit_events");
        jdbc.update("INSERT INTO audit_events(actor_id,action,target_type,target_id,outcome,occurred_at,correlation_id,metadata) "
                + "VALUES(100,'POST_APPROVED','POST_VERSION',7,'SUCCESS','2026-11-01','first','{}'),"
                + "(100,'POST_APPROVED','POST_VERSION',8,'SUCCESS','2026-11-01','second','{}'),"
                + "(101,'POST_DELETED','POST',7,'SUCCESS','2026-11-02','third','{}')");
        var response = mapper.readTree(mvc.perform(get("/api/v1/admin/audit-events").with(user(actor(true)))
                .param("from", "2026-11-01T00:00:00Z").param("to", "2026-11-03T00:00:00Z")
                .param("actor_id", "100").param("action", "POST_APPROVED").param("size", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(2, response.path("total_elements").asLong());
        assertEquals(2, response.path("total_pages").asInt());
        assertEquals(8, response.at("/content/0/target_id").asLong());
        assertEquals("second", response.at("/content/0/correlation_id").asText());
        assertEquals("2026-11-01T00:00:00Z", response.at("/content/0/occurred_at").asText());
        assertEquals("{}", response.at("/content/0/metadata").toString());
        mvc.perform(get("/api/v1/admin/audit-events").with(user(actor(true)))
                .param("from", "2026-11-01T01:00:00+01:00").param("to", "2026-11-02T00:00:00Z")
                .param("target_type", "POST_VERSION").param("target_id", "7").param("outcome", "SUCCESS"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total_elements").value(1)).andExpect(jsonPath("$.content[0].target_id").value(7));
        mvc.perform(get("/api/v1/admin/audit-events").with(user(actor(true)))
                .param("from", "2026-11-01T00:00:00Z").param("to", "2026-11-02T00:00:00Z")
                .param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].target_id").value(7));
        mvc.perform(get("/api/v1/admin/audit-events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/audit-events").with(user(actor(false)))).andExpect(status().isForbidden());
        for (String[] invalid : new String[][]{{"size", "101"}, {"page", "-1"}, {"action", "bad' OR 1=1"},
                {"target_id", "7"}, {"actor_id", "0"}, {"outcome", "bad"}, {"from", "bad"}, {"page", "10001"},
                {"from", "0999-01-01T00:00:00Z"}, {"to", "-999999999-01-01T00:00:00Z"}, {"to", "2026-11-01T00:00:00.000000001Z"}})
            mvc.perform(get("/api/v1/admin/audit-events").with(user(actor(true))).param(invalid[0], invalid[1]))
                    .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/admin/audit-events/1").with(user(actor(true)))).andExpect(status().is5xxServerError());
        mvc.perform(put("/api/v1/admin/audit-events/1").with(user(actor(true)))).andExpect(status().is5xxServerError());
    }

    @Test
    void roleMutationAuditCommitsAndRollsBackWithMutation() throws Exception {
        jdbc.update("DELETE FROM audit_events");
        jdbc.update("INSERT INTO users(email,is_deleted) VALUES('audit-target@example.test',false)");
        long target = jdbc.queryForObject("SELECT id FROM users WHERE email='audit-target@example.test'", Long.class);
        jdbc.update("INSERT INTO user_roles(user_id,role) VALUES(?,'ROLE_USER')", target);
        try {
            mvc.perform(put("/api/v1/admin/users/" + target + "/promote").with(user(actor(true))).header("X-Request-ID", "audit-role-123"))
                    .andExpect(status().isOk());
            var event = jdbc.queryForMap("SELECT * FROM audit_events WHERE action='ROLE_ADDED' AND target_id=?", target);
            assertEquals(999L, ((Number) event.get("actor_id")).longValue());
            assertEquals("audit-role-123", event.get("correlation_id"));
            assertEquals("ROLE_ADMIN", mapper.readTree(event.get("metadata").toString()).path("role").asText());
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                try { mvc.perform(put("/api/v1/admin/users/" + target + "/demote").with(user(actor(true)))).andExpect(status().isOk()); }
                catch (Exception e) { throw new RuntimeException(e); }
                tx.setRollbackOnly();
            });
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action='ROLE_REMOVED' AND target_id=?", Integer.class, target));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_roles WHERE user_id=? AND role='ROLE_ADMIN'", Integer.class, target));
        } finally {
            jdbc.update("DELETE FROM user_roles WHERE user_id=?", target);
            jdbc.update("DELETE FROM users WHERE id=?", target);
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE target_id=?", Integer.class, target));
    }

    static UserPrincipal actor(boolean admin) {
        User user = new User(); user.setId(999L); user.setEmail("actor@example.test"); user.addRole(admin ? "ROLE_ADMIN" : "ROLE_USER");
        return new UserPrincipal(user);
    }
}
