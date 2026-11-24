-- Expand/backfill/constrain: legacy creation times are unknown, never inferred.
-- All new metadata is UTC; this does not reinterpret legacy post/session clocks.
SET @profile_backfill_now = UTC_TIMESTAMP(6);

ALTER TABLE users
    ADD COLUMN created_at DATETIME(6) NULL,
    ADD COLUMN updated_at DATETIME(6) NULL,
    ADD COLUMN timestamps_approximate BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE subjects
    ADD COLUMN created_at DATETIME(6) NULL,
    ADD COLUMN updated_at DATETIME(6) NULL,
    ADD COLUMN timestamps_approximate BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE users SET created_at=@profile_backfill_now, updated_at=@profile_backfill_now;
UPDATE subjects SET created_at=@profile_backfill_now, updated_at=@profile_backfill_now;

ALTER TABLE users
    MODIFY created_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    MODIFY updated_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    MODIFY timestamps_approximate BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE subjects
    MODIFY created_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    MODIFY updated_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    MODIFY timestamps_approximate BOOLEAN NOT NULL DEFAULT FALSE;

-- ORM lifecycle callbacks maintain UTC modification times and immutable
-- creation/provenance fields for API writes, including association touches.
-- Direct SQL maintenance must explicitly set updated_at=UTC_TIMESTAMP(6).
-- No triggers/global privileges or legacy connection time-zone changes needed.
