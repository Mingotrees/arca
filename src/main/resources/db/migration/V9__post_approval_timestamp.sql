-- Approval provenance starts at deployment. Historical dates are unknown.
ALTER TABLE posts ADD COLUMN approved_at DATETIME(6) NULL;
