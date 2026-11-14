ALTER TABLE posts
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deleted_at DATETIME(6) NULL,
    ADD COLUMN deleted_by BIGINT NULL,
    ADD INDEX idx_posts_active_latest_status (is_deleted, is_latest_version, status, updated_at, id);

-- deleted_by is deliberately a non-cascading scalar actor ID, not a foreign key.
-- Actor removal must not erase deletion provenance or prevent account cleanup.
