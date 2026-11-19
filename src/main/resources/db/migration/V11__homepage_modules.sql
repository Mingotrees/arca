ALTER TABLE votes ADD COLUMN changed_at DATETIME(6) NULL;
CREATE INDEX idx_votes_trending_changed ON votes (changed_at, post_id, vote_type);
CREATE INDEX idx_posts_trending ON posts (is_deleted, is_latest_version, status, approved_at, id);

CREATE TABLE announcements (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    body TEXT NULL,
    url VARCHAR(2048) NULL,
    priority INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    starts_at DATETIME(6) NULL,
    ends_at DATETIME(6) NULL,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at DATETIME(6) NULL,
    INDEX idx_announcements_active (is_deleted, active, priority, id),
    INDEX idx_announcements_created (is_deleted, created_at, id),
    CONSTRAINT ck_announcements_priority CHECK (priority BETWEEN 0 AND 1000),
    CONSTRAINT ck_announcements_window CHECK (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT ck_announcements_content CHECK (body IS NOT NULL OR url IS NOT NULL)
) ENGINE=InnoDB;
