# DCISM database-account provisioning request

## Target and purpose

Database: `s22101440_arca`, MariaDB11.8.6 on the same server as the backend. Application connections currently originate locally. Provision a separate request-serving account so the application cannot alter audit history through SQL; retain separately controlled migration/operator access.

The currently inspected account has global USAGE and schema-wide ALL PRIVILEGES. Global CREATE USER / relevant GRANT OPTION must be confirmed by the administrator; ordinary schema-wide privileges are not equivalent to permission to create or delegate accounts. Do not attempt to bypass this using application credentials.

## Requested runtime access

Create one dedicated application account with a strong secret delivered through the approved protected configuration mechanism. Restrict host scope to the observed local connection identity, rather than a wildcard remote account. Account naming and exact localhost/127.0.0.1 matching follow DCISM administration policy.

Grant SELECT, INSERT, UPDATE, DELETE on application tables:

```text
announcements
banned_emails
department_subjects
departments
files
password_recovery_rate_limits
password_reset_tokens
post_identities
post_links
post_tags
posts
refresh_tokens
school
subject_aliases
subjects
user_roles
users
vaults
votes
```

Special cases:

- `audit_events`: SELECT and INSERT **only**. No UPDATE or DELETE.
- `flyway_schema_history`: no request-serving access required; Flyway uses the separate migration connection.
- No schema/global wildcard privileges, GRANT OPTION, account-management rights, or runtime DDL rights. Grants are additive; broader rights would defeat the table restrictions.

## Migration and retention access

The migration connection needs reviewed schema DDL and migration-history rights. The existing schema-wide account may be retained for this separately controlled purpose if the administrator approves; it must no longer be the application's ordinary datasource identity.

Authorized retention operators need SELECT/DELETE on audit_events for the approved manual365-day purge/export procedure. Their use is outside application APIs and should be recorded by the existing operator process.

## Provisioning sequence

1. An administrator can create the new account ahead of the maintenance window. Grant rights on the new tables after migrations through V11 create them; do not assume a grant against a nonexistent table succeeds.
2. During the reviewed release window, migrate using the controlled migration connection, apply/verify runtime grants, then update the backend's protected datasource credentials. Configure `spring.flyway.user` / `spring.flyway.password` separately from datasource credentials; keep existing valid JWT signing secret.
3. Verify application startup and normal writes, audit inserts/queries, and denied runtime audit UPDATE/DELETE. This split has already passed on the isolated MariaDB11.8.6 restored-backup rehearsal.
4. Recheck SHOW GRANTS privately and remove any broader inherited/schema rights. Never place passwords, account configuration, database dumps or private keys in Git or chat.

No production account/grant change is performed by this document. Current deployment authorization/access must be resolved before execution.
