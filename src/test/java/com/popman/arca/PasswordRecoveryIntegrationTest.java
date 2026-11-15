package com.popman.arca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popman.arca.service.recovery.RecoveryEmailSender;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.password-recovery.enabled=true", "app.password-recovery.reset-link-base-url=https://client.example.test/reset-password",
        "app.password-recovery.from=arca@example.test", "app.password-recovery.rate-key-secret=dGVzdC1vbmx5LXJhdGUta2V5LXdpdGgtYXQtbGVhc3QtMzItYnl0ZXM=",
        "app.password-recovery.trusted-proxies=10.0.0.1/32", "logging.level.com.popman.arca.filter.PostDeletionObservationFilter=INFO"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(PasswordRecoveryIntegrationTest.TestMail.class)
@ExtendWith(OutputCaptureExtension.class)
class PasswordRecoveryIntegrationTest {
    private static final String OLD = "OldPassword1!";
    private static final String NEW = "NewPassword2!";
    private static String oldHash;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired CapturingMail mail;
    @Autowired MutableClock clock;
    @Autowired PlatformTransactionManager transactions;
    @Autowired com.popman.arca.repository.RefreshTokenRepository refreshTokens;
    @Autowired io.micrometer.core.instrument.MeterRegistry meters;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired com.popman.arca.service.recovery.RecoveryMailDispatcher dispatcher;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("recoveryMailExecutor") org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor mailExecutor;
    @SpyBean PasswordEncoder passwords;

    @BeforeEach
    void seed() {
        mail.clear(); clock.reset();
        for (String table : new String[]{"password_reset_tokens", "password_recovery_rate_limits", "files", "votes", "vaults", "post_tags",
                "posts", "post_identities", "user_roles", "refresh_tokens", "department_subjects", "subject_aliases", "subjects", "departments", "school", "banned_emails", "users"}) {
            jdbc.update("DELETE FROM " + table);
        }
        if (oldHash == null) oldHash = passwords.encode(OLD);
        jdbc.update("INSERT INTO users(id,email,password,is_deleted) VALUES(1,'owner@example.test',?,false),"
                + "(2,'other@example.test',?,false),(3,'deleted@example.test',?,true),(4,'banned@example.test',?,false)", oldHash, oldHash, oldHash, oldHash);
        jdbc.update("INSERT INTO user_roles(user_id,role) VALUES(1,'ROLE_USER'),(2,'ROLE_USER'),(3,'ROLE_USER'),(4,'ROLE_USER')");
        jdbc.update("INSERT INTO banned_emails(email,created_at) VALUES('banned@example.test',NOW())");
    }

    @AfterEach
    void awaitMailCompletion() {
        if (mail.block != null) mail.block.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((mailExecutor.getActiveCount() > 0 || mailExecutor.getQueueSize() > 0) && System.nanoTime() < deadline) {
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        assertEquals(0, mailExecutor.getActiveCount()); assertEquals(0, mailExecutor.getQueueSize());
    }

    @Test
    void allEligibilityStatesShareGenericResponseAndOnlyHashesArePersisted(CapturedOutput output) throws Exception {
        String generic = recover(" Owner@Example.Test ");
        for (String email : new String[]{"missing@example.test", "deleted@example.test", "banned@example.test"}) assertEquals(generic, recover(email));
        Delivery delivery = mail.take();
        assertEquals("owner@example.test", delivery.email());
        String token = delivery.token();
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
        assertEquals("https", delivery.link().getScheme());
        assertNull(delivery.link().getQuery());
        var row = jdbc.queryForMap("SELECT * FROM password_reset_tokens");
        assertEquals(1L, ((Number) row.get("user_id")).longValue());
        assertTrue(row.get("token_hash").toString().matches("[0-9a-f]{64}"));
        assertFalse(row.toString().contains(token));
        assertFalse(generic.contains(token));
        assertFalse(output.getAll().contains(token));
        assertNull(mail.deliveries.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void successfulResetConsumesTokenRevokesEveryRefreshSessionAndPreservesAccessJwtLifetime() throws Exception {
        JsonNode first = login(OLD);
        login(OLD);
        assertEquals(2, count("refresh_tokens"));
        String token = issue();
        reset(token, NEW, 200);
        assertEquals(0, count("refresh_tokens"));
        assertEquals(1, count("password_reset_tokens WHERE used_at IS NOT NULL"));
        String stored = jdbc.queryForObject("SELECT password FROM users WHERE id=1", String.class);
        assertNotEquals(NEW, stored); assertTrue(passwords.matches(NEW, stored)); assertFalse(passwords.matches(OLD, stored));
        postJson("/api/v1/auth/login", Map.of("email", "owner@example.test", "password", OLD), 401);
        login(NEW);
        postJson("/api/v1/auth/refresh", Map.of("refresh_token", first.path("refresh_token").asText()), 401);
        mvc.perform(get("/api/v1/user/me").header("Authorization", "Bearer " + first.path("access_token").asText())).andExpect(status().isOk());
        reset(token, "AnotherPassword3!", 400);
    }

    @Test
    void legacyRefreshAndLogoutKeepOpaqueSessionAndResponseContracts() throws Exception {
        String refresh = login(OLD).path("refresh_token").asText();
        JsonNode renewed = mapper.readTree(postJson("/api/v1/auth/refresh", Map.of("refresh_token", refresh), 200).body());
        assertEquals(refresh, renewed.path("refresh_token").asText());
        assertEquals("owner@example.test", renewed.path("email").asText());
        assertFalse(renewed.path("access_token").asText().isBlank());
        postJson("/api/v1/auth/logout", Map.of("refresh_token", refresh), 200);
        assertEquals(0, count("refresh_tokens"));
        postJson("/api/v1/auth/refresh", Map.of("refresh_token", refresh), 401);
    }

    @Test
    void expiredSupersededAndRandomTokensCannotResetPasswords() throws Exception {
        String first = issue(); String second = issue();
        reset(first, NEW, 400);
        assertEquals(1, count("password_reset_tokens WHERE revoked_at IS NOT NULL"));
        clock.advance(Duration.ofMinutes(15));
        reset(second, NEW, 400);
        reset("z".repeat(43), NEW, 400);
        reset("malformed", NEW, 400);
        assertEquals(oldHash, jdbc.queryForObject("SELECT password FROM users WHERE id=1", String.class));
        assertEquals(0, count("password_reset_tokens WHERE used_at IS NOT NULL"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Short1", "lowercase1", "UPPERCASE1", "NoNumbersHere", "GoodPassword1éééééééééééééééééééééééééééééééé"})
    void invalidPasswordsNeverConsumeToken(String password) throws Exception {
        reset(issue(), password, 400);
        assertEquals(0, count("password_reset_tokens WHERE used_at IS NOT NULL"));
        assertEquals(oldHash, jdbc.queryForObject("SELECT password FROM users WHERE id=1", String.class));
    }

    @Test
    void disabledOrBannedAccountsCannotConsumePreviouslyIssuedTokens() throws Exception {
        String token = issue();
        jdbc.update("UPDATE users SET is_deleted=true WHERE id=1"); reset(token, NEW, 400);
        jdbc.update("UPDATE users SET is_deleted=false WHERE id=1");
        jdbc.update("INSERT INTO banned_emails(email,created_at) VALUES('owner@example.test',NOW())"); reset(token, NEW, 400);
        assertEquals(0, count("password_reset_tokens WHERE used_at IS NOT NULL"));
    }

    @Test
    void emailRateLimitNormalizesInputAndWindowExpiryAllowsRetry() throws Exception {
        recover("missing@example.test"); recover(" MISSING@example.test "); recover("missing@EXAMPLE.TEST");
        var response = mvc.perform(post("/api/v1/auth/password-recovery").header("Origin", "http://localhost:5173").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.test\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.status").value(429))
                .andExpect(header().string("Access-Control-Expose-Headers", org.hamcrest.Matchers.containsString("Retry-After")))
                .andReturn().getResponse();
        assertTrue(Long.parseLong(response.getHeader("Retry-After")) > 0);
        clock.advance(Duration.ofMinutes(15)); recover("missing@example.test");
        assertFalse(jdbc.queryForList("SELECT bucket_key FROM password_recovery_rate_limits").toString().contains("missing@example.test"));
    }

    @Test
    void untrustedForwardedHeadersCannotEvadeIpBudgetOrCreateUnboundedEmailBuckets() throws Exception {
        for (int i = 0; i < 11; i++) {
            mvc.perform(post("/api/v1/auth/password-recovery").with(request -> {request.setRemoteAddr("203.0.113.10"); return request;})
                            .header("X-Forwarded-For", "198.51.100." + i).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("email", "missing" + i + "@example.test"))))
                    .andExpect(status().is(i < 10 ? 202 : 429));
        }
        assertEquals(11, count("password_recovery_rate_limits"));
    }

    @Test
    void trustedProxyUsesLastUntrustedHopNotSpoofedLeftmostAddress() throws Exception {
        for (int i = 0; i < 11; i++) {
            mvc.perform(post("/api/v1/auth/password-recovery").with(request -> {request.setRemoteAddr("10.0.0.1"); return request;})
                            .header("X-Forwarded-For", "192.0.2." + i + ",198.51.100.20").contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("email", "proxy" + i + "@example.test"))))
                    .andExpect(status().is(i < 10 ? 202 : 429));
        }
    }

    @Test
    void resetRateBudgetSurvivesInvalidTokenTransactions() throws Exception {
        for (int i = 0; i < 21; i++) reset("z".repeat(43), NEW, i < 20 ? 400 : 429);
    }

    @Test
    void trustedProxyRejectsNonstandardIpShorthandWithoutDnsResolution() throws Exception {
        mvc.perform(post("/api/v1/auth/password-recovery").with(request -> {request.setRemoteAddr("10.0.0.1"); return request;})
                        .header("X-Forwarded-For", "127.1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.test\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recoveryCannotUseUnicodeCollationAliasesToBypassEmailRateBudget() throws Exception {
        postJson("/api/v1/auth/password-recovery", Map.of("email", "öwner@example.test"), 400);
        assertEquals(0, count("password_reset_tokens"));
    }

    @Test
    void cleanupRemovesOnlyExpiredRetentionRows() throws Exception {
        issue();
        var now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        jdbc.update("INSERT INTO password_reset_tokens(user_id,token_hash,created_at,expires_at) VALUES(1,?,?,?)",
                "a".repeat(64), now.minusDays(40), now.minusDays(31));
        jdbc.update("INSERT INTO password_recovery_rate_limits(bucket_key,requests,window_end) VALUES('expired-fixture',3,?)", now.minusDays(2));
        assertTrue(context.containsBean("passwordRecoveryMaintenance"), "Retention maintenance must be installed when recovery is enabled");
        context.getBean("passwordRecoveryMaintenance", Runnable.class).run();
        assertEquals(1, count("password_reset_tokens"));
        assertEquals(0, count("password_recovery_rate_limits WHERE bucket_key='expired-fixture'"));
        assertEquals(2, count("password_recovery_rate_limits"));
    }

    @Test
    void recoveryAndResetObservationsAreCorrelatedAndContentFree(CapturedOutput output) throws Exception {
        mvc.perform(post("/api/v1/auth/password-recovery").header("X-Request-ID", "recover-test-123")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"owner@example.test\"}"))
                .andExpect(status().isAccepted()).andExpect(header().string("X-Request-ID", "recover-test-123"));
        String token = mail.take().token();
        mvc.perform(post("/api/v1/auth/password-reset").header("X-Request-ID", "reset-test-123")
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("token", token, "new_password", NEW))))
                .andExpect(status().isOk()).andExpect(header().string("X-Request-ID", "reset-test-123"));
        assertNotNull(meters.find("arca.auth.password.recovery").tags("status", "202", "outcome", "success").timer());
        assertNotNull(meters.find("arca.auth.password.reset").tags("status", "200", "outcome", "success").timer());
        assertTrue(output.getAll().contains("correlation_id=recover-test-123"));
        assertFalse(output.getAll().contains(token)); assertFalse(output.getAll().contains(NEW));
        assertFalse(output.getAll().contains("owner@example.test"));
    }

    @Test
    void mailFailureKeepsGenericResponseAndRedactsProviderException(CapturedOutput output) throws Exception {
        mail.fail = true;
        double before = meters.counter("arca.auth.password.delivery", "outcome", "failed").count();
        String body = recover("owner@example.test"); Delivery delivery = mail.take();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (meters.counter("arca.auth.password.delivery", "outcome", "failed").count() == before && System.nanoTime() < deadline) {
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        assertTrue(meters.counter("arca.auth.password.delivery", "outcome", "failed").count() > before);
        assertEquals(body, recover("missing@example.test"));
        assertFalse(output.getAll().contains(delivery.token()));
        assertFalse(output.getAll().contains("owner@example.test"));
    }

    @Test
    void blockedProviderDoesNotBlockGenericHttpResponse() throws Exception {
        mail.block = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var response = executor.submit(() -> recover("owner@example.test"));
            try { mail.take(); assertNotNull(response.get(1, TimeUnit.SECONDS)); }
            finally { mail.block.countDown(); }
        }
    }

    @Test
    void rollbackSuppressesMailAndTokenButKeepsAbuseBudget() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            try { recover("owner@example.test"); } catch (Exception e) { throw new RuntimeException(e); }
            tx.setRollbackOnly();
        });
        assertEquals(0, count("password_reset_tokens"));
        assertEquals(2, count("password_recovery_rate_limits"));
        assertNull(mail.deliveries.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void concurrentTokenConsumptionSucceedsOnlyOnce() throws Exception {
        String token = issue();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Integer> attempt = () -> { start.await(); return postJson("/api/v1/auth/password-reset", Map.of("token", token, "new_password", NEW), -1).status(); };
            var a = executor.submit(attempt); var b = executor.submit(attempt); start.countDown();
            assertEquals(java.util.List.of(200, 400), java.util.stream.Stream.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)).sorted().toList());
        }
        assertEquals(1, count("password_reset_tokens WHERE used_at IS NOT NULL"));
    }

    @Test
    void concurrentRecoveryBudgetCannotExceedPerEmailLimit() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            var start = new CountDownLatch(1); var attempts = new java.util.ArrayList<Future<Integer>>();
            for (int i = 0; i < 8; i++) attempts.add(executor.submit(() -> {
                start.await(); return postJson("/api/v1/auth/password-recovery", Map.of("email", "missing@example.test"), -1).status();
            }));
            start.countDown(); int accepted = 0; int rejected = 0;
            for (var attempt : attempts) { int code = attempt.get(10, TimeUnit.SECONDS); if (code == 202) accepted++; else if (code == 429) rejected++; }
            assertEquals(3, accepted); assertEquals(5, rejected);
        }
    }

    @Test
    void concurrentRecoveryIssuanceLeavesOnlyOneUsableToken() throws Exception {
        try (var executor = Executors.newFixedThreadPool(3)) {
            var a = executor.submit(() -> recover("owner@example.test"));
            var b = executor.submit(() -> recover("owner@example.test"));
            var c = executor.submit(() -> recover("owner@example.test"));
            assertEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)); c.get(10, TimeUnit.SECONDS);
        }
        var deliveries = java.util.List.of(mail.take(), mail.take(), mail.take());
        assertEquals(1, count("password_reset_tokens WHERE used_at IS NULL AND revoked_at IS NULL"));
        int successes = 0;
        for (var delivery : deliveries) if (postJson("/api/v1/auth/password-reset", Map.of("token", delivery.token(), "new_password", NEW), -1).status() == 200) successes++;
        assertEquals(1, successes);
    }

    @Test
    void fullMailQueueDropsSafelyWithoutLeakingResetLink(CapturedOutput output) throws Exception {
        mail.block = new CountDownLatch(1);
        URI link = URI.create("https://client.example.test/reset#token=" + "q".repeat(43));
        double before = meters.counter("arca.auth.password.delivery", "outcome", "queue_full").count();
        try {
            for (int i = 0; i < 2; i++) new TransactionTemplate(transactions).executeWithoutResult(tx -> dispatcher.afterCommit("owner@example.test", link));
            mail.take(); mail.take();
            for (int i = 0; i < 101; i++) new TransactionTemplate(transactions).executeWithoutResult(tx -> dispatcher.afterCommit("owner@example.test", link));
            assertEquals(100, mailExecutor.getQueueSize());
            assertEquals(before + 1, meters.counter("arca.auth.password.delivery", "outcome", "queue_full").count());
            assertFalse(output.getAll().contains(link.toString()));
        } finally { mail.block.countDown(); }
    }

    @Test
    void resetWaitsForValidatedLoginThenRevokesItsNewSession() throws Exception {
        String token = issue(); var authenticated = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(invocation -> {
            boolean matches = (boolean) invocation.callRealMethod();
            if (matches && OLD.contentEquals(invocation.getArgument(0, CharSequence.class))) {
                authenticated.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS));
            }
            return matches;
        }).when(passwords).matches(any(CharSequence.class), anyString());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var login = executor.submit(() -> login(OLD));
            try {
                assertTrue(authenticated.await(10, TimeUnit.SECONDS));
                var resetting = executor.submit(() -> reset(token, NEW, 200));
                assertThrows(TimeoutException.class, () -> resetting.get(300, TimeUnit.MILLISECONDS));
                release.countDown(); login.get(10, TimeUnit.SECONDS); resetting.get(10, TimeUnit.SECONDS);
            } finally { release.countDown(); }
        }
        assertEquals(0, count("refresh_tokens"));
    }

    @Test
    void refreshSnapshotTakenBeforeResetCannotMintAccessAfterResetCommits() throws Exception {
        String refresh = login(OLD).path("refresh_token").asText(); String token = issue();
        var loaded = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var refreshing = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                assertTrue(refreshTokens.findByToken(refresh).isPresent()); loaded.countDown();
                try {
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                    postJson("/api/v1/auth/refresh", Map.of("refresh_token", refresh), 401);
                } catch (Exception e) { throw new RuntimeException(e); }
                finally { tx.setRollbackOnly(); }
            }));
            try { assertTrue(loaded.await(10, TimeUnit.SECONDS)); reset(token, NEW, 200); }
            finally { release.countDown(); }
            refreshing.get(10, TimeUnit.SECONDS);
        }
        assertEquals(0, count("refresh_tokens"));
    }

    private String issue() throws Exception { recover("owner@example.test"); return mail.take().token(); }
    private String recover(String email) throws Exception { return postJson("/api/v1/auth/password-recovery", Map.of("email", email), 202).body(); }
    private Integer reset(String token, String password, int expected) throws Exception {
        return postJson("/api/v1/auth/password-reset", Map.of("token", token, "new_password", password), expected).status();
    }
    private JsonNode login(String password) throws Exception { return mapper.readTree(postJson("/api/v1/auth/login", Map.of("email", "owner@example.test", "password", password), 200).body()); }
    private Reply postJson(String path, Object body, int expected) throws Exception {
        var result = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andReturn().getResponse();
        if (expected >= 0) assertEquals(expected, result.getStatus(), path);
        return new Reply(result.getStatus(), result.getContentAsString());
    }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    record Reply(int status, String body) {}
    record Delivery(String email, URI link) { String token() { return link.getFragment().substring("token=".length()); } }

    @TestConfiguration
    static class TestMail {
        @Bean @Primary CapturingMail capturingMail() { return new CapturingMail(); }
        @Bean @Primary MutableClock recoveryTestClock() { return new MutableClock(); }
    }
    static class CapturingMail implements RecoveryEmailSender {
        final BlockingQueue<Delivery> deliveries = new LinkedBlockingQueue<>();
        volatile boolean fail; volatile CountDownLatch block;
        public void sendResetLink(String email, URI link) {
            deliveries.add(new Delivery(email, link));
            try { if (block != null) assertTrue(block.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            if (fail) throw new IllegalStateException("SMTP diagnostic must not leak " + link);
        }
        Delivery take() throws Exception { Delivery value = deliveries.poll(5, TimeUnit.SECONDS); assertNotNull(value); return value; }
        void clear() { if (block != null) block.countDown(); block = null; fail = false; deliveries.clear(); }
    }
    static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>();
        void reset() { instant.set(Instant.parse("2026-11-15T00:00:00Z")); }
        void advance(Duration duration) { instant.updateAndGet(value -> value.plus(duration)); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return instant.get(); }
    }
}
