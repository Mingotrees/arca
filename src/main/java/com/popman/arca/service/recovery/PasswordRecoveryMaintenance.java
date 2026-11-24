package com.popman.arca.service.recovery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;

@Component
@ConditionalOnProperty(name = "app.password-recovery.enabled", havingValue = "true")
public class PasswordRecoveryMaintenance implements Runnable {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public PasswordRecoveryMaintenance(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    @Override
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
    public void run() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        // Bounded batches; never delete unexpired tokens or active rate windows.
        for (int i = 0; i < 10; i++) {
            int tokens = jdbc.update("DELETE FROM password_reset_tokens WHERE expires_at<? LIMIT 10000", now.minusDays(30));
            int rates = jdbc.update("DELETE FROM password_recovery_rate_limits WHERE window_end<? LIMIT 10000", now.minusDays(1));
            if (tokens < 10000 && rates < 10000) break;
        }
    }
}
