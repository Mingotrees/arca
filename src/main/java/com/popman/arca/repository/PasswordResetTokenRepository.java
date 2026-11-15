package com.popman.arca.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class PasswordResetTokenRepository {
    private final JdbcTemplate jdbc;
    public PasswordResetTokenRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void create(long userId, String hash, LocalDateTime now, LocalDateTime expires) {
        jdbc.update("INSERT INTO password_reset_tokens(user_id,token_hash,created_at,expires_at) VALUES(?,?,?,?)", userId, hash, now, expires);
    }

    public Optional<Token> find(String hash, boolean forUpdate) {
        return jdbc.query("SELECT user_id,expires_at,used_at,revoked_at FROM password_reset_tokens WHERE token_hash=?"
                        + (forUpdate ? " FOR UPDATE" : ""), (row, number) -> new Token(row.getLong("user_id"),
                row.getObject("expires_at", LocalDateTime.class), row.getObject("used_at", LocalDateTime.class),
                row.getObject("revoked_at", LocalDateTime.class)), hash).stream().findFirst();
    }

    public void revokeUnused(long userId, LocalDateTime now) {
        jdbc.update("UPDATE password_reset_tokens SET revoked_at=? WHERE user_id=? AND used_at IS NULL AND revoked_at IS NULL", now, userId);
    }

    public boolean consume(String hash, LocalDateTime now) {
        return jdbc.update("UPDATE password_reset_tokens SET used_at=? WHERE token_hash=? AND used_at IS NULL "
                + "AND revoked_at IS NULL AND expires_at>?", now, hash, now) == 1;
    }

    public void deleteRefreshSessions(long userId) { jdbc.update("DELETE FROM refresh_tokens WHERE user_id=?", userId); }

    public record Token(long userId, LocalDateTime expires, LocalDateTime used, LocalDateTime revoked) {
        public boolean usableAt(LocalDateTime now) { return used == null && revoked == null && expires.isAfter(now); }
    }
}
