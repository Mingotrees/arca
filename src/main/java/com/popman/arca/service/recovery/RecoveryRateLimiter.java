package com.popman.arca.service.recovery;

import com.popman.arca.config.PasswordRecoveryProperties;
import com.popman.arca.exceptions.RecoveryRateLimitException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.HexFormat;

@Service
public class RecoveryRateLimiter {
    private final JdbcTemplate jdbc;
    private final PasswordRecoveryProperties settings;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public RecoveryRateLimiter(JdbcTemplate jdbc, PasswordRecoveryProperties settings, Clock clock, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.settings = settings; this.clock = clock;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public void recovery(String email, String ip) {
        long wait = transaction.execute(tx -> {
            long ipWait = acquire("recovery:ip:", ip, settings.ipLimit());
            // Do not allocate arbitrary email buckets once this peer's IP budget is exhausted.
            return ipWait > 0 ? ipWait : acquire("recovery:email:", email, settings.emailLimit());
        });
        if (wait > 0) throw new RecoveryRateLimitException(wait);
    }

    public void reset(String ip) {
        long wait = transaction.execute(tx -> acquire("reset:ip:", ip, settings.resetIpLimit()));
        if (wait > 0) throw new RecoveryRateLimitException(wait);
    }

    private long acquire(String domain, String value, int limit) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        String key = domain + digest(domain + value);
        jdbc.update("INSERT INTO password_recovery_rate_limits(bucket_key,requests,window_end) VALUES(?,0,?) "
                + "ON DUPLICATE KEY UPDATE bucket_key=bucket_key", key, now.plus(settings.rateWindow()));
        var row = jdbc.queryForObject("SELECT requests,window_end FROM password_recovery_rate_limits WHERE bucket_key=? FOR UPDATE",
                (result, number) -> new Bucket(result.getInt("requests"), result.getObject("window_end", LocalDateTime.class)), key);
        LocalDateTime end = row.end();
        int requests = row.requests();
        if (!end.isAfter(now)) { end = now.plus(settings.rateWindow()); requests = 0; }
        if (requests >= limit) return Math.max(1, (Duration.between(now, end).toMillis() + 999) / 1000);
        jdbc.update("UPDATE password_recovery_rate_limits SET requests=?,window_end=? WHERE bucket_key=?", requests + 1, end, key);
        return 0;
    }

    private String digest(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(settings.rateKeyBytes(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException("Recovery rate-key initialization failed"); }
    }
    private record Bucket(int requests, LocalDateTime end) {}
}
