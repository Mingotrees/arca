-- Clean-install schema matching the pre-roadmap entity mappings.
-- Existing installations must be inventoried and explicitly baselined at 1;
-- this script must never be run over an existing application schema.
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(255), last_name VARCHAR(255), password VARCHAR(255),
    email VARCHAR(255) NOT NULL, course VARCHAR(24), department VARCHAR(255),
    bio VARCHAR(50), profile_picture VARCHAR(255), is_deleted BIT, deleted_at DATETIME(6),
    CONSTRAINT uk_users_email UNIQUE (email),
    INDEX idx_user_id (id), INDEX idx_email (email), INDEX idx_deleted (is_deleted)
) ENGINE=InnoDB;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL, role VARCHAR(255),
    INDEX idx_user_roles_user_id (user_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE school (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255), INDEX idx_school_id (id)
) ENGINE=InnoDB;

CREATE TABLE departments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(34), school_id BIGINT NOT NULL,
    CONSTRAINT fk_departments_school FOREIGN KEY (school_id) REFERENCES school(id)
) ENGINE=InnoDB;

CREATE TABLE subjects (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, code VARCHAR(10), name VARCHAR(255),
    CONSTRAINT uk_subjects_code UNIQUE (code), CONSTRAINT uk_subjects_name UNIQUE (name),
    INDEX idx_subject_id (id)
) ENGINE=InnoDB;

CREATE TABLE subject_aliases (
    subject_id BIGINT NOT NULL, alias VARCHAR(255),
    CONSTRAINT fk_subject_aliases_subject FOREIGN KEY (subject_id) REFERENCES subjects(id)
) ENGINE=InnoDB;

CREATE TABLE department_subjects (
    subject_id BIGINT NOT NULL, department_id BIGINT NOT NULL,
    CONSTRAINT fk_department_subjects_subject FOREIGN KEY (subject_id) REFERENCES subjects(id),
    CONSTRAINT fk_department_subjects_department FOREIGN KEY (department_id) REFERENCES departments(id)
) ENGINE=InnoDB;

CREATE TABLE posts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, post_id INT NOT NULL, version INT NOT NULL,
    title VARCHAR(30) NOT NULL, content TEXT NOT NULL, status VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL, department_id BIGINT NOT NULL, is_latest_version BIT NOT NULL,
    created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, rejection_reason TEXT,
    INDEX idx_post_id_version (post_id, version), INDEX idx_user_id (user_id),
    INDEX idx_department_id (department_id), INDEX idx_status (status),
    CONSTRAINT fk_posts_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_posts_department FOREIGN KEY (department_id) REFERENCES departments(id)
) ENGINE=InnoDB;

CREATE TABLE post_tags (
    post_id BIGINT NOT NULL, subject_id BIGINT NOT NULL, PRIMARY KEY (post_id, subject_id),
    CONSTRAINT fk_post_tags_post FOREIGN KEY (post_id) REFERENCES posts(id),
    CONSTRAINT fk_post_tags_subject FOREIGN KEY (subject_id) REFERENCES subjects(id)
) ENGINE=InnoDB;

CREATE TABLE files (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, file_name VARCHAR(255), file_path VARCHAR(255),
    file_size BIGINT, file_type VARCHAR(255), user_id BIGINT NOT NULL, post_id BIGINT NOT NULL,
    INDEX idx_file_id (id),
    CONSTRAINT fk_files_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_files_post FOREIGN KEY (post_id) REFERENCES posts(id)
) ENGINE=InnoDB;

CREATE TABLE votes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, post_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
    vote_type VARCHAR(255) NOT NULL, CONSTRAINT uk_votes_post_user UNIQUE (post_id, user_id),
    CONSTRAINT fk_votes_post FOREIGN KEY (post_id) REFERENCES posts(id),
    CONSTRAINT fk_votes_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE vaults (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, post_id BIGINT NOT NULL,
    label TEXT, INDEX idx_vault_id (id), CONSTRAINT uk_vault_user_post UNIQUE (user_id, post_id),
    CONSTRAINT fk_vaults_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_vaults_post FOREIGN KEY (post_id) REFERENCES posts(id)
) ENGINE=InnoDB;

CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL,
    token VARCHAR(255) NOT NULL, expiry_date DATETIME(6) NOT NULL,
    CONSTRAINT uk_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE banned_emails (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, email VARCHAR(255) NOT NULL,
    reason VARCHAR(255), created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_banned_emails_email UNIQUE (email), INDEX idx_banned_email_email (email)
) ENGINE=InnoDB;
