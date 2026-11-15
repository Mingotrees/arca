-- Search targets MySQL 8.4/InnoDB, the built-in natural-language full-text
-- parser, utf8mb4_0900_ai_ci (case/accent insensitive), default stopwords,
-- innodb_ft_min_token_size=3 and innodb_ft_max_token_size=84.
-- Operators must inventory parser settings and rehearse this DDL on a backup:
-- first FULLTEXT creation may rebuild the table and block writes.
-- Explicitly widen/normalize only the searched text columns; retain all values,
-- field names, nullability and length limits. Never change applied V1-V3 files.
ALTER TABLE posts
    MODIFY title VARCHAR(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    MODIFY content TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    ADD FULLTEXT INDEX ft_posts_title_content (title, content),
    ADD INDEX idx_posts_search_created (is_deleted, is_latest_version, status, created_at, id),
    ADD INDEX idx_posts_search_department (department_id, is_deleted, is_latest_version, status, created_at, id),
    ADD INDEX idx_posts_search_author (user_id, is_deleted, is_latest_version, status, created_at, id);

ALTER TABLE post_tags ADD INDEX idx_post_tags_subject_post (subject_id, post_id);
