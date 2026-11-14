package com.popman.arca;

import com.popman.arca.entity.User;
import com.popman.arca.entity.UserPrincipal;
import com.popman.arca.repository.PostRepository;
import com.popman.arca.service.PostService;
import com.popman.arca.dto.v1.post.PostUpdateRequest;
import com.popman.arca.dto.v1.post.PostApprovalRequest;
import com.popman.arca.dto.v1.post.PostRequest;
import com.popman.arca.dto.v1.post.PostCreateResponse;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.concurrent.atomic.AtomicReference;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "logging.level.com.popman.arca.filter.PostDeletionObservationFilter=INFO")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class PostDeletionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PostRepository posts;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired PostService postService;
    @Autowired MeterRegistry meters;
    @TempDir Path media;
    Path storedFile;

    @BeforeEach
    void seed() throws Exception {
        for (String table : new String[]{"files", "votes", "vaults", "post_tags", "posts", "post_identities", "user_roles",
                "refresh_tokens", "department_subjects", "subject_aliases", "subjects", "departments", "school", "users"}) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO users (id,email,is_deleted) VALUES "
                + "(1,'owner@example.test',false),(2,'other@example.test',false),(3,'admin@example.test',false)");
        jdbc.update("INSERT INTO user_roles (user_id,role) VALUES (1,'ROLE_USER'),(2,'ROLE_USER'),(3,'ROLE_ADMIN')");
        jdbc.update("INSERT INTO school (id,name) VALUES (1,'School')");
        jdbc.update("INSERT INTO departments (id,name,school_id) VALUES (1,'Department',1)");
        jdbc.update("INSERT INTO subjects (id,code,name) VALUES (1,'CODE','Subject')");
        jdbc.update("INSERT INTO post_identities (id,owner_id) VALUES (7,1),(8,2)");
        jdbc.update("INSERT INTO posts (id,post_id,version,title,content,status,user_id,department_id,"
                + "is_latest_version,created_at,updated_at) VALUES "
                + "(10,7,1,'Old','Body','APPROVED',1,1,true,NOW(),NOW()),"
                + "(11,7,2,'New','Body','PENDING_APPROVAL',1,1,true,NOW(),NOW()),"
                + "(12,8,1,'Keep','Body','APPROVED',2,1,true,NOW(),NOW())");
        jdbc.update("INSERT INTO post_tags (post_id,subject_id) VALUES (10,1),(11,1),(12,1)");
        storedFile = Files.writeString(media.resolve("retained.pdf"), "%PDF-retained");
        jdbc.update("INSERT INTO files (id,file_name,file_path,post_id,user_id) VALUES (1,'retained.pdf',?,10,1)",
                storedFile.toString());
        jdbc.update("INSERT INTO votes (id,post_id,user_id,vote_type) VALUES (1,10,2,'UPVOTE')");
        jdbc.update("INSERT INTO vaults (id,post_id,user_id,label) VALUES (1,10,2,'Keep label')");
    }

    @Test
    void ownerDeletesAllVersionsThroughAnyRowAndRetainsFilesAndRelatedRows() throws Exception {
        mvc.perform(delete("/api/v1/posts/11").with(user(principal(1, false))))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertEquals(2, count("posts WHERE post_id=7 AND is_deleted=true AND deleted_by=1 AND deleted_at IS NOT NULL"));
        assertEquals(1, count("posts WHERE post_id=8 AND is_deleted=false"));
        assertEquals(1, count("files"));
        assertEquals(1, count("votes"));
        assertEquals(1, count("vaults"));
        assertTrue(Files.exists(storedFile));
        Map<String,Object> provenance = jdbc.queryForMap("SELECT deleted_at,deleted_by FROM posts WHERE id=10");
        mvc.perform(delete("/api/v1/posts/10").with(user(principal(3, true))))
                .andExpect(status().isNoContent());
        assertEquals(provenance, jdbc.queryForMap("SELECT deleted_at,deleted_by FROM posts WHERE id=10"));
    }

    @Test
    void adminCanDeleteAndUnauthorizedCallsCannotMutate() throws Exception {
        mvc.perform(delete("/api/v1/posts/10")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/posts/10").with(user(principal(2, false))))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/posts/9999").with(user(principal(1, false))))
                .andExpect(status().isNotFound());
        assertEquals(0, count("posts WHERE is_deleted=true"));
        mvc.perform(delete("/api/v1/posts/10").with(user(principal(3, true))))
                .andExpect(status().isNoContent());
        assertEquals(2, count("posts WHERE is_deleted=true AND deleted_by=3"));
        mvc.perform(delete("/api/v1/posts/11").with(user(principal(2, false))))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/posts/10", "/api/v1/posts/11", "/api/v1/files/1",
            "/api/v1/files/download/1", "/api/v1/votes/10/my-vote", "/api/v1/votes/10/upvotes",
            "/api/v1/votes/10/downvotes", "/api/v1/vaults/1"})
    void deletedResourcesAreNotReadableEvenByAdmin(String path) throws Exception {
        markDeleted();
        for (UserPrincipal actor : new UserPrincipal[]{principal(1, false), principal(2, false), principal(3, true)}) {
            mvc.perform(get(path).with(user(actor))).andExpect(status().isNotFound());
        }
    }

    @Test
    void deletedPostsDisappearFromEveryListAndHistoryQuery() throws Exception {
        markDeleted();
        for (String path : new String[]{"/api/v1/posts", "/api/v1/posts/subject/1",
                "/api/v1/posts/department/1", "/api/v1/posts/user/2"}) {
            mvc.perform(get(path).with(user(principal(2, false))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(12));
        }
        for (String path : new String[]{"/api/v1/posts/history/7", "/api/v1/posts/pending",
                "/api/v1/posts/pending/department/1", "/api/v1/posts/mine", "/api/v1/posts/mine?status=APPROVED",
                "/api/v1/posts/user/1"}) {
            mvc.perform(get(path).with(user(principal(1, true))))
                    .andExpect(status().isOk()).andExpect(content().json("[]"));
        }
        mvc.perform(get("/api/v1/vaults/user").with(user(principal(2, false))))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/v1/vaults/check/10").with(user(principal(2, false))))
                .andExpect(status().isNotFound());
        assertTrue(posts.findPreviousApprovedVersion(7, 2) == null);
    }

    @Test
    void deletedPostsRejectWritesWithoutChangingRetainedRows() throws Exception {
        markDeleted();
        mvc.perform(put("/api/v1/posts/10").with(user(principal(1, false)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Change\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/posts/11/approve").with(user(principal(3, true)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":false}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/votes").with(user(principal(2, false)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"post_id\":10,\"vote_type\":\"DOWNVOTE\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/votes/10").with(user(principal(2, false))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/vaults/add").with(user(principal(1, false)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"post_id\":10,\"label\":\"New\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/vaults/edit-label").with(user(principal(2, false)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"post_id\":10,\"new_label\":\"Change\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(multipart("/api/v1/files/upload")
                        .file(new MockMultipartFile("file","test.pdf","application/pdf","%PDF-body".getBytes()))
                        .param("post_id","10").with(user(principal(1, false))))
                .andExpect(status().isNotFound());
        assertEquals(3, count("posts"));
        assertEquals("UPVOTE", jdbc.queryForObject("SELECT vote_type FROM votes WHERE id=1", String.class));
        assertEquals("Keep label", jdbc.queryForObject("SELECT label FROM vaults WHERE id=1", String.class));
        assertEquals(1, count("files"));
    }

    @Test
    void failedTransactionRollsBackDeletionOfEveryVersion() {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            postService.deletePostV1(11L, 1L, false);
            tx.setRollbackOnly();
        });
        assertEquals(0, count("posts WHERE is_deleted=true OR deleted_at IS NOT NULL OR deleted_by IS NOT NULL"));
        assertEquals(2, count("posts WHERE post_id=7"));
    }

    @Test
    void deletionWaitsForNewVersionAndThenDeletesItToo() throws Exception {
        runSerialized(
                () -> postService.updatePostV1(new PostUpdateRequest(), 10L, 1L, false),
                () -> postService.deletePostV1(11L, 1L, false));
        assertEquals(3, count("posts WHERE post_id=7 AND is_deleted=true"));
        assertEquals(0, count("posts WHERE post_id=7 AND is_deleted=false"));
    }

    @Test
    void updateOnDifferentVersionWaitsForDeletionAndCannotResurrectPost() throws Exception {
        runSerialized(
                () -> postService.deletePostV1(11L, 1L, false),
                () -> assertEquals(404, assertThrows(ResponseStatusException.class,
                        () -> postService.updatePostV1(new PostUpdateRequest(), 10L, 1L, false))
                        .getStatusCode().value()));
        assertEquals(2, count("posts WHERE post_id=7 AND is_deleted=true"));
    }

    @Test
    void moderationWaitsForDeletionAndCannotRestoreAnApprovedVersion() throws Exception {
        PostApprovalRequest rejection = new PostApprovalRequest();
        rejection.setApproved(false);
        runSerialized(
                () -> postService.deletePostV1(10L, 1L, false),
                () -> assertEquals(404, assertThrows(ResponseStatusException.class,
                        () -> postService.approvePostV1(rejection, 11L)).getStatusCode().value()));
        assertEquals("PENDING_APPROVAL", jdbc.queryForObject("SELECT status FROM posts WHERE id=11", String.class));
    }

    @Test
    void concurrentCreatesHaveDistinctLogicalIdsAndDeletionCannotCrossOwners() throws Exception {
        CountDownLatch firstCreated = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicReference<PostCreateResponse> firstResponse = new AtomicReference<>();
        PostCreateResponse secondResponse;
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> first = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                firstResponse.set(postService.createPostV1(newPost(), 1L));
                firstCreated.countDown();
                await(releaseFirst);
            }));
            try {
                assertTrue(firstCreated.await(10, TimeUnit.SECONDS));
                secondResponse = postService.createPostV1(newPost(), 2L);
                assertNotEquals(firstResponse.get().getPostId(), secondResponse.getPostId(),
                        "Uncommitted creates must not allocate the same logical identity");
            } finally {
                releaseFirst.countDown();
            }
            first.get(10, TimeUnit.SECONDS);
        }
        postService.deletePostV1(secondResponse.getId(), 2L, false);
        assertEquals(false, jdbc.queryForObject("SELECT is_deleted FROM posts WHERE id=?", Boolean.class,
                firstResponse.get().getId()));
        assertEquals(true, jdbc.queryForObject("SELECT is_deleted FROM posts WHERE id=?", Boolean.class,
                secondResponse.getId()));
    }

    @Test
    void databaseRejectsDuplicateVersionsAndMixedOwnerLogicalGroups() {
        assertThrows(DataIntegrityViolationException.class, () -> insertVersion(1, 1));
        assertThrows(DataIntegrityViolationException.class, () -> insertVersion(3, 2));
    }

    @Test
    void deletionHasCorrelatedContentFreeOutcomeLogAndLatencyMetric(CapturedOutput output) throws Exception {
        var existing = meters.find("arca.post.deletion").tags("status", "204", "outcome", "success").timer();
        long before = existing == null ? 0 : existing.count();
        mvc.perform(delete("/api/v1/posts/11").with(user(principal(1, false)))
                        .header("X-Request-ID", "delete-regression-123")
                        .header("X-Private", "do-not-log-header").content("do-not-log-body"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("X-Request-ID", "delete-regression-123"));
        var metric = meters.find("arca.post.deletion").tags("status", "204", "outcome", "success").timer();
        assertNotNull(metric);
        assertEquals(before + 1, metric.count());
        assertTrue(metric.totalTime(TimeUnit.NANOSECONDS) > 0);
        assertTrue(output.getAll().contains("correlation_id=delete-regression-123"));
        assertFalse(output.getAll().contains("do-not-log-header"));
        assertFalse(output.getAll().contains("do-not-log-body"));
    }

    @Test
    void unauthenticatedDeletionGetsSafeCorrelationAndRejectedMetric() throws Exception {
        var response = mvc.perform(delete("/api/v1/posts/11").header("X-Request-ID", "unsafe\nvalue"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse();
        assertNotNull(response.getHeader("X-Request-ID"));
        assertTrue(response.getHeader("X-Request-ID").matches("[0-9a-f-]{36}"));
        var metric = meters.find("arca.post.deletion").tags("status", "401", "outcome", "rejected").timer();
        assertNotNull(metric);
        assertTrue(metric.count() >= 1);
        mvc.perform(get("/actuator/metrics").with(user(principal(2, false))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics/arca.post.deletion").with(user(principal(3, true))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("arca.post.deletion"));
    }

    @Test
    void concurrentVersionCreationReadsCurrentVersionAfterWaiting() throws Exception {
        runSerialized(
                () -> postService.updatePostV1(new PostUpdateRequest(), 10L, 1L, false),
                () -> postService.updatePostV1(new PostUpdateRequest(), 10L, 1L, false));
        assertEquals(4, count("posts WHERE post_id=7"));
        assertEquals(4, jdbc.queryForObject("SELECT MAX(version) FROM posts WHERE post_id=7", Integer.class));
    }

    private void insertVersion(int version, long ownerId) {
        jdbc.update("INSERT INTO posts (post_id,version,title,content,status,user_id,department_id,"
                + "is_latest_version,created_at,updated_at) VALUES (7,?,'Collision','Body','APPROVED',?,1,true,NOW(),NOW())",
                version, ownerId);
    }

    private PostRequest newPost() {
        PostRequest request = new PostRequest();
        request.setTitle("Independent post");
        request.setContent("Body");
        request.setDepartmentId(1L);
        return request;
    }

    private void runSerialized(Runnable firstMutation, Runnable secondMutation) throws Exception {
        CountDownLatch firstWritten = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                firstMutation.run();
                firstWritten.countDown();
                await(releaseFirst);
            }));
            try {
                assertTrue(firstWritten.await(10, TimeUnit.SECONDS), "First writer did not reach commit barrier");
                Future<?> second = executor.submit(() -> {
                    secondStarted.countDown();
                    secondMutation.run();
                });
                assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS),
                        "Second writer must wait for the logical-post lock");
                releaseFirst.countDown();
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
            } finally {
                releaseFirst.countDown();
            }
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for transaction barrier");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private void markDeleted() {
        jdbc.update("UPDATE posts SET is_deleted=true,deleted_by=1,deleted_at=NOW() WHERE post_id=7");
    }

    private int count(String tableAndWhere) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tableAndWhere, Integer.class);
    }

    private UserPrincipal principal(long id, boolean admin) {
        User user = new User();
        user.setId(id);
        user.setEmail("actor" + id + "@example.test");
        user.addRole(admin ? "ROLE_ADMIN" : "ROLE_USER");
        return new UserPrincipal(user);
    }
}
