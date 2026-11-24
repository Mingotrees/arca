CREATE TABLE post_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    url VARCHAR(2048) NOT NULL,
    normalized_url TEXT NOT NULL,
    normalized_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    label VARCHAR(200) NULL,
    position INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    updated_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (id),
    CONSTRAINT fk_post_links_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    CONSTRAINT uk_post_links_url UNIQUE (post_id, normalized_hash),
    CONSTRAINT uk_post_links_position UNIQUE (post_id, position),
    CONSTRAINT chk_post_links_position CHECK (position >= 0 AND position < 10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
