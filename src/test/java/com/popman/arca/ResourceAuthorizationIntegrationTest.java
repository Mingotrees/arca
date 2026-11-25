package com.popman.arca;

import com.popman.arca.entity.User;
import com.popman.arca.entity.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import com.popman.arca.service.impl.FileServiceImplementation;
import com.popman.arca.service.impl.UserServiceImplementation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class ResourceAuthorizationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired FileServiceImplementation files;
    @Autowired UserServiceImplementation users;
    @Autowired PlatformTransactionManager transactions;
    @Autowired com.popman.arca.repository.UserRepository userRepository;
    @TempDir Path media;

    @BeforeEach
    void seed() throws Exception {
        ReflectionTestUtils.setField(files, "uploadDir", media.toString());
        ReflectionTestUtils.setField(users, "uploadDir", media.toString());
        for (String table : new String[]{"files", "votes", "vaults", "post_tags", "posts", "post_identities",
                "user_roles", "refresh_tokens", "department_subjects", "subject_aliases", "subjects",
                "departments", "school", "users"}) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO users (id,email,is_deleted) VALUES "
                + "(1,'owner@example.test',false),(2,'other@example.test',false),(3,'admin@example.test',false)");
        jdbc.update("INSERT INTO user_roles (user_id,role) VALUES (1,'ROLE_USER'),(2,'ROLE_USER'),(3,'ROLE_ADMIN')");
        jdbc.update("INSERT INTO school (id,name) VALUES (1,'School')");
        jdbc.update("INSERT INTO departments (id,name,school_id) VALUES (1,'Department',1)");
        jdbc.update("INSERT INTO post_identities (id,owner_id) VALUES (7,1)");
        jdbc.update("INSERT INTO posts (id,post_id,version,title,content,status,user_id,department_id,"
                + "is_latest_version,created_at,updated_at) VALUES "
                + "(10,7,1,'Public','Body','APPROVED',1,1,true,NOW(),NOW()),"
                + "(11,7,2,'Private','Secret','PENDING_APPROVAL',1,1,true,NOW(),NOW())");
        jdbc.update("INSERT INTO files (id,file_name,file_path,file_type,post_id,user_id) "
                + "VALUES (1,'test.pdf','uploads/posts/10/test.pdf','application/pdf',10,1)");
        jdbc.update("INSERT INTO votes (id,post_id,user_id,vote_type) VALUES (1,10,2,'UPVOTE')");
        jdbc.update("INSERT INTO vaults (id,post_id,user_id,label) VALUES (1,10,2,'Saved')");
        Path document = Files.createDirectories(media.resolve("posts/10")).resolve("test.pdf");
        Files.writeString(document, "%PDF-1.7");
        jdbc.update("UPDATE files SET file_path=? WHERE id=1", document.toString());
    }

    @ParameterizedTest
    @CsvSource({"APPROVED,1,200", "APPROVED,2,200", "APPROVED,3,200",
            "PENDING_APPROVAL,1,200", "PENDING_APPROVAL,2,404", "PENDING_APPROVAL,3,200",
            "REJECTED,1,200", "REJECTED,2,404", "REJECTED,3,200",
            "SUPERSEDED,1,200", "SUPERSEDED,2,404", "SUPERSEDED,3,200",
            "DELETED,1,404", "DELETED,2,404", "DELETED,3,404"})
    void parentVisibilityProtectsDirectReadsFilesAndVoteReads(String state, long actor, int expected) throws Exception {
        state(state);
        for (String path : new String[]{"/api/v1/posts/10", "/api/v1/files/1",
                "/api/v1/files/download/1", "/api/v1/votes/10/upvotes", "/api/v1/votes/10/downvotes"}) {
            mvc.perform(get(path).with(user(principal(actor)))).andExpect(status().is(expected));
        }
        mvc.perform(get("/api/v1/votes/10/my-vote").with(user(principal(actor))))
                .andExpect(status().is(expected == 200 && actor != 2 ? 204 : expected));
    }

    @ParameterizedTest
    @CsvSource(value = {"{\"title\":\"Edited\"}", "{\"retain_attachment_ids\":null}"}, quoteCharacter = '\'')
    void textEditReusesBytesAndOrdinaryReaderCanDownloadAfterApproval(String edit) throws Exception {
        long original = createAttachmentPost();
        long oldFile = uploadAttachment(original, "%PDF-1.7 original");
        long secondFile = uploadAttachment(original, "%PDF-1.7 original");
        approveAttachmentPost(original);

        long revision = editAttachmentPost(original, edit);
        var newIds = jdbc.queryForList("SELECT id FROM files WHERE post_id=? ORDER BY id", Long.class, revision);
        assertEquals(2, newIds.size());
        assertTrue(Collections.disjoint(newIds, List.of(oldFile, secondFile)));
        assertEquals(jdbc.queryForList("SELECT file_path FROM files WHERE post_id=? ORDER BY file_path", String.class, original),
                jdbc.queryForList("SELECT file_path FROM files WHERE post_id=? ORDER BY file_path", String.class, revision));
        for (long newFile : newIds) {
            mvc.perform(get("/api/v1/files/download/" + newFile).with(user(principal(2))))
                    .andExpect(status().isNotFound());
        }

        approveAttachmentPost(revision);
        mvc.perform(get("/api/v1/posts/" + revision).with(user(principal(2))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.files.length()").value(2));
        for (long newFile : newIds) assertDownload(newFile, 2, "%PDF-1.7 original");
    }

    @Test
    void keepListAndReplacementLeavePreviousRevisionUntouched() throws Exception {
        long original = createAttachmentPost();
        long oldFile = uploadAttachment(original, "%PDF-1.7 old");
        long keepFile = uploadAttachment(original, "%PDF-1.7 keep");
        approveAttachmentPost(original);

        long revision = editAttachmentPost(original, "{\"retain_attachment_ids\":[" + keepFile + "]}");
        long retainedFile = firstAttachment(revision, 1);
        assertNotEquals(keepFile, retainedFile);
        long newFile = uploadAttachment(revision, "%PDF-1.7 replacement");
        approveAttachmentPost(revision);

        mvc.perform(get("/api/v1/posts/" + revision).with(user(principal(2))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.files.length()").value(2));
        assertDownload(retainedFile, 2, "%PDF-1.7 keep");
        assertDownload(newFile, 2, "%PDF-1.7 replacement");
        assertDownload(oldFile, 1, "%PDF-1.7 old");
        assertDownload(keepFile, 1, "%PDF-1.7 keep");

        long cleared = editAttachmentPost(revision, "{\"retain_attachment_ids\":[]}");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM files WHERE post_id=?", Integer.class, cleared));
    }

    @Test
    void invalidKeepListsDoNotCreateARevision() throws Exception {
        long original = createAttachmentPost();
        long file = uploadAttachment(original, "%PDF-1.7 original");
        approveAttachmentPost(original);
        for (String ids : new String[]{"[1]", "[" + file + "," + file + "]", "[null]", "[0]", "[1,2,3,4]",
                "[\"" + file + "\"]", "[" + file + ".5]"}) {
            mvc.perform(put("/api/v1/posts/" + original).with(user(principal(1)))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"retain_attachment_ids\":" + ids + "}"))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE post_id=(SELECT post_id FROM posts WHERE id=?)", Integer.class, original));
    }

    private long createAttachmentPost() throws Exception {
        return mapper.readTree(mvc.perform(post("/api/v1/posts").with(user(principal(1)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"With attachments\",\"content\":\"Body\",\"department_id\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).required("id").asLong();
    }

    private long uploadAttachment(long row, String bytes) throws Exception {
        mvc.perform(multipart("/api/v1/files/upload")
                        .file(new MockMultipartFile("file", "test.pdf", "application/pdf", bytes.getBytes(StandardCharsets.UTF_8)))
                        .param("post_id", Long.toString(row)).with(user(principal(1))))
                .andExpect(status().isOk());
        return jdbc.queryForObject("SELECT MAX(id) FROM files WHERE post_id=?", Long.class, row);
    }

    private void approveAttachmentPost(long row) throws Exception {
        mvc.perform(post("/api/v1/posts/" + row + "/approve").with(user(principal(3)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"))
                .andExpect(status().isOk());
    }

    private long editAttachmentPost(long row, String body) throws Exception {
        return mapper.readTree(mvc.perform(put("/api/v1/posts/" + row).with(user(principal(1)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).required("id").asLong();
    }

    private long firstAttachment(long row, long actor) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/v1/posts/" + row).with(user(principal(actor))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.files.length()").value(1))
                .andReturn().getResponse().getContentAsString()).requiredAt("/files/0/id").asLong();
    }

    private void assertDownload(long file, long actor, String bytes) throws Exception {
        mvc.perform(get("/api/v1/files/download/" + file).with(user(principal(actor))))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void historyFiltersEachVersionWithoutExposingPrivateBodies() throws Exception {
        mvc.perform(get("/api/v1/posts/history/7").with(user(principal(2))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(10));
        for (long actor : new long[]{1, 3}) {
            mvc.perform(get("/api/v1/posts/history/7").with(user(principal(actor))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        }
    }

    @ParameterizedTest
    @CsvSource({"APPROVED,200", "PENDING_APPROVAL,404", "REJECTED,404", "SUPERSEDED,404", "DELETED,404"})
    void vaultReadsAndWritesUseParentVisibility(String state, int expected) throws Exception {
        state(state);
        mvc.perform(get("/api/v1/vaults/1").with(user(principal(2)))).andExpect(status().is(expected));
        mvc.perform(get("/api/v1/vaults/check/10").with(user(principal(2)))).andExpect(status().is(expected));
        mvc.perform(get("/api/v1/vaults/user").with(user(principal(2))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(expected == 200 ? 1 : 0));
        if (expected == 404) {
            mvc.perform(post("/api/v1/vaults/add").with(user(principal(2)))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"post_id\":10}"))
                    .andExpect(status().isNotFound());
            mvc.perform(put("/api/v1/vaults/edit-label").with(user(principal(2)))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"post_id\":10,\"new_label\":\"Changed\"}"))
                    .andExpect(status().isNotFound());
            mvc.perform(delete("/api/v1/votes/10").with(user(principal(2))))
                    .andExpect(status().isNotFound());
            assertEquals("Saved", jdbc.queryForObject("SELECT label FROM vaults WHERE id=1", String.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM votes", Integer.class));
        }
    }

    @ParameterizedTest
    @CsvSource({"APPROVED,403", "PENDING_APPROVAL,404", "REJECTED,404", "SUPERSEDED,404", "DELETED,404"})
    void inaccessibleMutationsConcealExistenceAndNeverChangeData(String state, int expected) throws Exception {
        state(state);
        mvc.perform(put("/api/v1/posts/10").with(user(principal(2)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Changed\"}"))
                .andExpect(status().is(expected));
        mvc.perform(delete("/api/v1/posts/10").with(user(principal(2)))).andExpect(status().is(expected));
        mvc.perform(multipart("/api/v1/files/upload")
                        .file(new MockMultipartFile("file", "test.pdf", "application/pdf", "%PDF-1.7".getBytes()))
                        .param("post_id", "10").param("user_id", "1").with(user(principal(2))))
                .andExpect(status().is(expected));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class));
        assertEquals("Public", jdbc.queryForObject("SELECT title FROM posts WHERE id=10", String.class));
    }

    @Test
    void guessedVaultIdsStayPrivateEvenForAdminAndAnonymousReadsRequireAuthentication() throws Exception {
        for (long actor : new long[]{1, 3}) {
            mvc.perform(get("/api/v1/vaults/1").with(user(principal(actor)))).andExpect(status().isNotFound());
        }
        for (String path : new String[]{"/api/v1/posts/10", "/api/v1/posts/history/7", "/api/v1/files/1",
                "/api/v1/files/download/1", "/api/v1/votes/10/upvotes", "/api/v1/vaults/1"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void creatingPostIgnoresLegacyOwnershipInput() throws Exception {
        mvc.perform(post("/api/v1/posts").with(user(principal(2))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Own post\",\"content\":\"Body\",\"department_id\":1,\"user_id\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user_id").value(2));
        assertEquals(2L, jdbc.queryForObject("SELECT user_id FROM posts WHERE title='Own post'", Long.class));
    }

    @Test
    void downloadSanitizesLegacyNamesAndNeverFollowsSymlinks() throws Exception {
        jdbc.update("UPDATE files SET file_name=? WHERE id=1", "../unsafe\"\r\nInjected: yes.pdf");
        var response = mvc.perform(get("/api/v1/files/download/1").with(user(principal(2))))
                .andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();
        String disposition = response.getHeader("Content-Disposition");
        org.junit.jupiter.api.Assertions.assertFalse(disposition.contains("\r") || disposition.contains("\n") || disposition.contains("../"));
        Path outside = Files.writeString(media.resolveSibling(media.getFileName() + "-outside.pdf"), "secret");
        try {
            Path link = media.resolve("posts/10/link.pdf");
            Files.createSymbolicLink(link, outside);
            jdbc.update("UPDATE files SET file_path=? WHERE id=1", link.toString());
            mvc.perform(get("/api/v1/files/download/1").with(user(principal(2)))).andExpect(status().isNotFound());
        } finally { Files.deleteIfExists(outside); }
    }

    @Test
    void avatarReplacementRetainsOldFileOnRollbackAndRemovesNewFile() throws Exception {
        Path old = Files.createDirectories(media.resolve("profile-pictures")).resolve("old.png");
        Files.write(old, png());
        jdbc.update("UPDATE users SET profile_picture='profile-pictures/old.png' WHERE id=1");
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            try {
                mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/user/1/profile-picture")
                                .file(new MockMultipartFile("file", "avatar.png", "image/png", png()))
                                .with(user(principal(1)))).andExpect(status().isOk());
                org.junit.jupiter.api.Assertions.assertTrue(Files.exists(old));
            } catch (Exception e) { throw new RuntimeException(e); }
            tx.setRollbackOnly();
        });
        assertEquals("profile-pictures/old.png", jdbc.queryForObject("SELECT profile_picture FROM users WHERE id=1", String.class));
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(old));
        try (var paths = Files.list(old.getParent())) { assertEquals(1, paths.count()); }
    }

    @Test
    void invalidVotesAndVaultRequestsReturnJsonValidationErrors() throws Exception {
        for (String body : new String[]{"{\"post_id\":10,\"vote_type\":\"INVALID\"}", "{\"post_id\":10}", "{}"}) {
            mvc.perform(post("/api/v1/votes").with(user(principal(2))).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        mvc.perform(post("/api/v1/vaults/add").with(user(principal(2))).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        assertEquals("UPVOTE", jdbc.queryForObject("SELECT vote_type FROM votes WHERE id=1", String.class));
    }

    @Test
    void hiddenAndMissingFilesHaveIndistinguishableErrorResponses() throws Exception {
        jdbc.update("UPDATE posts SET status='PENDING_APPROVAL' WHERE id=10");
        for (String prefix : new String[]{"/api/v1/files/", "/api/v1/files/download/"}) {
            var hidden = mvc.perform(get(prefix + "1").with(user(principal(2))))
                    .andExpect(status().isNotFound()).andReturn().getResponse();
            var missing = mvc.perform(get(prefix + "9999").with(user(principal(2))))
                    .andExpect(status().isNotFound()).andReturn().getResponse();
            assertEquals(missing.getContentType(), hidden.getContentType());
            // Timestamps may differ; the public error shape and message must not.
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            if (!missing.getContentAsString().isEmpty()) {
                var hiddenJson = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(hidden.getContentAsString());
                var missingJson = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(missing.getContentAsString());
                hiddenJson.remove("timestamp"); missingJson.remove("timestamp");
                assertEquals(missingJson, hiddenJson);
            } else assertEquals("", hidden.getContentAsString());
        }
    }

    @Test
    void duplicateVaultSaveReturnsConflictEnvelope() throws Exception {
        mvc.perform(post("/api/v1/vaults/add").with(user(principal(2))).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"post_id\":10}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void validUploadsIgnoreSpoofedActorAndRollbackRemovesAttachment() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            try {
                mvc.perform(multipart("/api/v1/files/upload")
                                .file(new MockMultipartFile("file", "../../document.pdf", "application/pdf", "%PDF-1.7".getBytes()))
                                .param("post_id", "11").param("user_id", "2").with(user(principal(1))))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.files[0].user_id").value(1));
            } catch (Exception e) { throw new RuntimeException(e); }
            tx.setRollbackOnly();
        });
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class));
        try (var paths = Files.list(media.resolve("posts/11"))) { assertEquals(0, paths.count()); }
    }

    @Test
    void avatarReplacementCommitsAndKeepsPublicReadCompatibility() throws Exception {
        Path old = Files.createDirectories(media.resolve("profile-pictures")).resolve("old.png");
        Files.write(old, png());
        jdbc.update("UPDATE users SET profile_picture='profile-pictures/old.png' WHERE id=1");
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/user/1/profile-picture")
                        .file(new MockMultipartFile("file", "avatar.png", "image/png", png())).with(user(principal(3))))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(old));
        mvc.perform(get("/api/v1/user/1/profile-picture"))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void staleProfileEditCannotRestoreDeletedAvatarReference() throws Exception {
        Path old = Files.createDirectories(media.resolve("profile-pictures")).resolve("old.png");
        Files.write(old, png());
        jdbc.update("UPDATE users SET profile_picture='profile-pictures/old.png' WHERE id=1");
        var loaded = new java.util.concurrent.CountDownLatch(1);
        var resume = new java.util.concurrent.CountDownLatch(1);
        String replacement;
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var edit = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                // Same persistence context as updateUserV1: model the row loaded before another request commits.
                assertEquals("profile-pictures/old.png", userRepository.findById(1L).orElseThrow().getProfilePicture());
                loaded.countDown();
                try {
                    org.junit.jupiter.api.Assertions.assertTrue(resume.await(10, java.util.concurrent.TimeUnit.SECONDS));
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                var request = new com.popman.arca.dto.v1.user.UserUpdateRequest();
                request.setBio("Concurrent edit");
                users.updateUserV1(1L, request);
            }));
            try {
                org.junit.jupiter.api.Assertions.assertTrue(loaded.await(10, java.util.concurrent.TimeUnit.SECONDS));
                mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/user/1/profile-picture")
                                .file(new MockMultipartFile("file", "avatar.png", "image/png", png())).with(user(principal(1))))
                        .andExpect(status().isOk());
                replacement = jdbc.queryForObject("SELECT profile_picture FROM users WHERE id=1", String.class);
            } finally { resume.countDown(); }
            edit.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(replacement, jdbc.queryForObject("SELECT profile_picture FROM users WHERE id=1", String.class));
        assertEquals("Concurrent edit", jdbc.queryForObject("SELECT bio FROM users WHERE id=1", String.class));
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(old));
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(media.resolve(replacement)));
        mvc.perform(get("/api/v1/user/1/profile-picture")).andExpect(status().isOk());
    }

    @Test
    void documentedRequestPropertyNamesWorkThroughMvc() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var schemas = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("components").path("schemas");
        var voteProperties = schemas.path("VoteRequest").path("properties");
        String votePost = voteProperties.has("post_id") ? "post_id" : "postId";
        String voteType = voteProperties.has("vote_type") ? "vote_type" : "voteType";
        mvc.perform(post("/api/v1/votes").with(user(principal(2))).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of(votePost, 10, voteType, "DOWNVOTE"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.vote_type").value("DOWNVOTE"));
        var vaultProperties = schemas.path("VaultRequest").path("properties");
        String vaultPost = vaultProperties.has("post_id") ? "post_id" : "postId";
        mvc.perform(post("/api/v1/vaults/add").with(user(principal(1))).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of(vaultPost, 11, "label", "Documented"))))
                .andExpect(status().isOk());
        var labelProperties = schemas.path("EditVaultLabelRequest").path("properties");
        String labelPost = labelProperties.has("post_id") ? "post_id" : "postId";
        String label = labelProperties.has("new_label") ? "new_label" : "newLabel";
        mvc.perform(put("/api/v1/vaults/edit-label").with(user(principal(2))).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of(labelPost, 10, label, "From docs"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.label").value("From docs"));
        var postProperties = schemas.path("PostRequest").path("properties");
        String department = postProperties.has("department_id") ? "department_id" : "departmentId";
        mvc.perform(post("/api/v1/posts").with(user(principal(2))).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("title", "From docs", "content", "Body", department, 1))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user_id").value(2));
        org.junit.jupiter.api.Assertions.assertTrue(postProperties.path("user_id").path("deprecated").asBoolean());
    }

    @ParameterizedTest
    @CsvSource({"1,200", "2,403", "3,200"})
    void coverWritesRequireOwnerOrAdmin(long actor, int expected) throws Exception {
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/user/1/cover-picture")
                        .file(new MockMultipartFile("file", "../../cover.png", "image/png", png()))
                        .with(user(principal(actor))))
                .andExpect(status().is(expected));
        mvc.perform(delete("/api/v1/user/1/cover-picture").with(user(principal(actor))))
                .andExpect(status().is(expected));
    }

    @Test
    void coverLifecycleIsPublicDiscoverableAndIndependentOfAvatarAndAttachments() throws Exception {
        Path avatar = Files.createDirectories(media.resolve("profile-pictures")).resolve("keep.png");
        Files.write(avatar, png());
        jdbc.update("UPDATE users SET profile_picture='profile-pictures/keep.png' WHERE id=1");
        mvc.perform(get("/api/v1/user/1/cover-picture")).andExpect(status().isNotFound());
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/user/1/cover-picture")
                .file(new MockMultipartFile("file", "cover.png", "image/png", png())))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/user/1/cover-picture")).andExpect(status().isUnauthorized());
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/user/1/cover-picture")
                        .file(new MockMultipartFile("file", "../../cover.png", "image/png", png())).with(user(principal(1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user_id").value(1))
                .andExpect(jsonPath("$.cover_picture").value(org.hamcrest.Matchers.matchesPattern("cover-pictures/[0-9a-f-]{36}\\.png")));
        String old = jdbc.queryForObject("SELECT cover_picture FROM users WHERE id=1", String.class);
        mvc.perform(get("/api/v1/user/1").with(user(principal(2))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cover_picture").value(old));
        mvc.perform(get("/api/v1/user/1/cover-picture"))
                .andExpect(status().isOk()).andExpect(content().bytes(png())).andExpect(content().contentType("image/png"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"cover-picture\""));
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/user/1/cover-picture")
                        .file(new MockMultipartFile("file", "cover.png", "image/png", png())).with(user(principal(3))))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(media.resolve(old)));
        String replacement = jdbc.queryForObject("SELECT cover_picture FROM users WHERE id=1", String.class);
        mvc.perform(delete("/api/v1/user/1/cover-picture").with(user(principal(1)))).andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(media.resolve(replacement)));
        mvc.perform(delete("/api/v1/user/1/cover-picture").with(user(principal(1)))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/user/1/cover-picture")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/user/1/profile-picture")).andExpect(status().isOk()).andExpect(content().bytes(png()));
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(media.resolve("posts/10/test.pdf")));
    }

    @Test
    void coverReplacementAndRemovalRollbackRetainUsableOldObject() throws Exception {
        // Use HTTP first so absence of the feature fails before any schema assumptions.
        mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/user/1/cover-picture")
                        .file(new MockMultipartFile("file", "cover.png", "image/png", png())).with(user(principal(1))))
                .andExpect(status().isOk());
        String old = jdbc.queryForObject("SELECT cover_picture FROM users WHERE id=1", String.class);
        for (boolean remove : new boolean[]{false, true}) {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                try {
                    if (remove) mvc.perform(delete("/api/v1/user/1/cover-picture").with(user(principal(1)))).andExpect(status().isOk());
                    else mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/user/1/cover-picture")
                                    .file(new MockMultipartFile("file", "cover.png", "image/png", png())).with(user(principal(1))))
                            .andExpect(status().isOk());
                    org.junit.jupiter.api.Assertions.assertTrue(Files.exists(media.resolve(old)));
                } catch (Exception e) { throw new RuntimeException(e); }
                tx.setRollbackOnly();
            });
            assertEquals(old, jdbc.queryForObject("SELECT cover_picture FROM users WHERE id=1", String.class));
            mvc.perform(get("/api/v1/user/1/cover-picture")).andExpect(status().isOk()).andExpect(content().bytes(png()));
            try (var paths = Files.list(media.resolve("cover-pictures"))) { assertEquals(1, paths.count()); }
        }
    }

    @Test
    void coverValidationRejectsInvalidImagesWithoutReferencesOrOrphans() throws Exception {
        byte[] bomb = png();
        java.nio.ByteBuffer.wrap(bomb).putInt(16, 100_000).putInt(20, 100_000);
        for (MockMultipartFile invalid : new MockMultipartFile[]{
                new MockMultipartFile("file", "cover.png", "image/png", new byte[0]),
                new MockMultipartFile("file", "cover.png", "image/png", "spoof".getBytes()),
                new MockMultipartFile("file", "cover.pdf", "application/pdf", "%PDF-1.7".getBytes()),
                new MockMultipartFile("file", "cover.png", "image/png", bomb),
                new MockMultipartFile("file", "cover.png", "image/png", new byte[10 * 1024 * 1024 + 1])}) {
            var response = mvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/v1/user/1/cover-picture")
                    .file(invalid).with(user(principal(1)))).andReturn().getResponse();
            org.junit.jupiter.api.Assertions.assertTrue(java.util.Set.of(400, 413, 415).contains(response.getStatus()),
                    "Expected validation error, got " + response.getStatus());
            org.junit.jupiter.api.Assertions.assertNull(jdbc.queryForObject("SELECT cover_picture FROM users WHERE id=1", String.class));
            org.junit.jupiter.api.Assertions.assertFalse(Files.exists(media.resolve("cover-pictures")));
        }
    }

    @Test
    void coverMissingObjectsAndCorruptKeysNeverReadOrDeleteOtherMedia() throws Exception {
        mvc.perform(get("/api/v1/user/999/cover-picture")).andExpect(status().isNotFound());
        for (String key : new String[]{"cover-pictures/missing.png", "posts/10/test.pdf", "../outside.png"}) {
            jdbc.update("UPDATE users SET cover_picture=? WHERE id=1", key);
            mvc.perform(get("/api/v1/user/1/cover-picture")).andExpect(status().isNotFound());
            mvc.perform(delete("/api/v1/user/1/cover-picture").with(user(principal(1)))).andExpect(status().isOk());
            org.junit.jupiter.api.Assertions.assertNull(jdbc.queryForObject("SELECT cover_picture FROM users WHERE id=1", String.class));
            org.junit.jupiter.api.Assertions.assertTrue(Files.exists(media.resolve("posts/10/test.pdf")));
        }
    }

    @Test
    void staleProfileEditCannotResurrectRemovedCover() throws Exception {
        Path old = Files.createDirectories(media.resolve("cover-pictures")).resolve("old.png");
        Files.write(old, png());
        jdbc.update("UPDATE users SET cover_picture='cover-pictures/old.png' WHERE id=1");
        var loaded = new java.util.concurrent.CountDownLatch(1);
        var resume = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var edit = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                assertEquals("cover-pictures/old.png", userRepository.findById(1L).orElseThrow().getCoverPicture());
                loaded.countDown();
                try { org.junit.jupiter.api.Assertions.assertTrue(resume.await(10, java.util.concurrent.TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                var request = new com.popman.arca.dto.v1.user.UserUpdateRequest();
                request.setBio("Concurrent cover edit");
                users.updateUserV1(1L, request);
            }));
            try {
                org.junit.jupiter.api.Assertions.assertTrue(loaded.await(10, java.util.concurrent.TimeUnit.SECONDS));
                mvc.perform(delete("/api/v1/user/1/cover-picture").with(user(principal(1)))).andExpect(status().isOk());
            } finally { resume.countDown(); }
            edit.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        org.junit.jupiter.api.Assertions.assertNull(jdbc.queryForObject("SELECT cover_picture FROM users WHERE id=1", String.class));
        assertEquals("Concurrent cover edit", jdbc.queryForObject("SELECT bio FROM users WHERE id=1", String.class));
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(old));
        mvc.perform(get("/api/v1/user/1/cover-picture")).andExpect(status().isNotFound());
    }

    private byte[] png() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }

    private void state(String state) {
        if (state.equals("DELETED")) jdbc.update("UPDATE posts SET is_deleted=true WHERE post_id=7");
        else jdbc.update("UPDATE posts SET status=? WHERE id=10", state);
    }

    private UserPrincipal principal(long id) {
        User actor = new User();
        actor.setId(id);
        actor.setEmail("actor" + id + "@example.test");
        actor.addRole(id == 3 ? "ROLE_ADMIN" : "ROLE_USER");
        return new UserPrincipal(actor);
    }
}
