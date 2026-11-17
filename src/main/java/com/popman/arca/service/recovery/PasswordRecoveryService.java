package com.popman.arca.service.recovery;

import com.popman.arca.config.PasswordRecoveryProperties;
import com.popman.arca.repository.*;
import com.popman.arca.service.BannedEmailService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class PasswordRecoveryService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final PasswordRecoveryProperties settings;
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final BannedEmailService banned;
    private final PasswordEncoder passwords;
    private final RecoveryRateLimiter rates;
    private final RecoveryMailDispatcher mail;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final com.popman.arca.service.audit.AuditService audit;

    public PasswordRecoveryService(PasswordRecoveryProperties settings, UserRepository users, PasswordResetTokenRepository tokens,
            BannedEmailService banned, PasswordEncoder passwords, RecoveryRateLimiter rates, RecoveryMailDispatcher mail,
            Clock clock, PlatformTransactionManager manager, com.popman.arca.service.audit.AuditService audit) {
        this.settings = settings; this.users = users; this.tokens = tokens; this.banned = banned;
        this.passwords = passwords; this.rates = rates; this.mail = mail; this.clock = clock;
        this.audit = audit;
        transaction = new TransactionTemplate(manager); transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public void request(String suppliedEmail, String ip) {
        requireEnabled();
        String email = suppliedEmail.strip().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !email.matches(com.popman.arca.dto.v1.auth.PasswordRecoveryRequest.EMAIL_PATTERN)) {
            throw new IllegalArgumentException("Email must use the existing ASCII account format");
        }
        // Rate work completes before starting account work, avoiding nested-transaction pool starvation.
        rates.recovery(email, ip);
        byte[] random = new byte[32]; RANDOM.nextBytes(random);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String hash = hash(raw); // Same random/digest work for every eligibility state.
        transaction.executeWithoutResult(tx -> {
            boolean blocked = banned.isBannedV1(email);
            var user = users.findByEmailForUpdate(email).orElse(null);
            if (user == null || blocked) return;
            LocalDateTime now = now();
            tokens.revokeUnused(user.getId(), now);
            tokens.create(user.getId(), hash, now, now.plus(settings.tokenTtl()));
            mail.afterCommit(user.getEmail(), URI.create(settings.resetLinkBaseUrl().toASCIIString() + "#token=" + raw));
        });
    }

    public void reset(String raw, String password, String ip) {
        requireEnabled(); validatePassword(password); rates.reset(ip);
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}")) throw invalidToken();
        String hash = hash(raw);
        transaction.executeWithoutResult(tx -> {
            var candidate = tokens.find(hash, false).orElseThrow(PasswordRecoveryService::invalidToken);
            // Every account mutation/session writer locks user FIRST, then re-reads token state.
            var user = users.findByIdForUpdate(candidate.userId()).orElseThrow(PasswordRecoveryService::invalidToken);
            var token = tokens.find(hash, true).orElseThrow(PasswordRecoveryService::invalidToken);
            LocalDateTime now = now();
            if (!token.usableAt(now) || banned.isBannedV1(user.getEmail())) throw invalidToken();
            if (!tokens.consume(hash, now)) throw invalidToken();
            user.setPassword(passwords.encode(password)); users.saveAndFlush(user);
            tokens.revokeUnused(user.getId(), now);
            tokens.deleteRefreshSessions(user.getId());
            audit.success(com.popman.arca.service.audit.AuditAction.PASSWORD_RESET, user.getId(), user.getId());
        });
    }

    private void requireEnabled() {
        if (!settings.enabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Password recovery is not configured");
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("Reset digest unavailable"); }
    }
    private static ResponseStatusException invalidToken() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token"); }
    private static void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !password.matches("(?s).*[A-Z].*") || !password.matches("(?s).*[a-z].*") || !password.matches("(?s).*\\d.*")) {
            throw new IllegalArgumentException("Password requires at least 8 characters, uppercase, lowercase and a digit, and at most 72 UTF-8 bytes");
        }
    }
}
