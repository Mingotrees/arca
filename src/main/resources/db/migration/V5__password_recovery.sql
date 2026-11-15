-- Only digests are persisted; raw reset tokens and delivery URLs must never be stored.
-- New timestamp columns are UTC DATETIME(6), independent of legacy post wall clocks.
CREATE TABLE password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    CONSTRAINT uk_password_reset_hash UNIQUE (token_hash),
    INDEX idx_password_reset_user (user_id, used_at, revoked_at),
    INDEX idx_password_reset_expiry (expires_at),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Domain-prefixed HMAC digests only: no plaintext email, IP or reset token.
CREATE TABLE password_recovery_rate_limits (
    bucket_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    requests INT UNSIGNED NOT NULL,
    window_end DATETIME(6) NOT NULL,
    INDEX idx_password_recovery_rate_expiry (window_end)
) ENGINE=InnoDB;
