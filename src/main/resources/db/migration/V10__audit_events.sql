CREATE TABLE audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT NULL,
    action VARCHAR(40) NOT NULL,
    target_type VARCHAR(24) NOT NULL,
    target_id BIGINT NULL,
    outcome VARCHAR(16) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    metadata JSON NOT NULL,
    INDEX idx_audit_time (occurred_at, id),
    INDEX idx_audit_actor (actor_id, occurred_at, id),
    INDEX idx_audit_action (action, occurred_at, id),
    INDEX idx_audit_target (target_type, target_id, occurred_at, id),
    INDEX idx_audit_outcome (outcome, occurred_at, id),
    CONSTRAINT ck_audit_outcome CHECK (outcome IN ('SUCCESS','DENIED','ERROR','ACCEPTED'))
) ENGINE=InnoDB;

-- Runtime account: SELECT, INSERT only on this table. Retention operator: SELECT, DELETE.
-- Apply table-specific grants outside Flyway; no UPDATE/DELETE application path exists.
