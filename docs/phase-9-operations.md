# Phase 9 — Audit events

## Query and export

`GET /api/v1/admin/audit-events` requires an admin bearer JWT, enforced both by HTTP rules and service method security. Anonymous callers receive JSON 401, ordinary users JSON 403. Response: `content`, `page`, `size`, `total_elements`, `total_pages`. Events contain `id`, nullable numeric `actor_id`, `action`, `target_type`, nullable numeric `target_id`, `outcome`, UTC `occurred_at`, `correlation_id`, and `metadata`.

Zero-based page defaults to 0, size to 20. Bounds: page 0–10000, size 1–100. Stable ordering: `occurred_at DESC,id DESC`. Filters intersect: exact action/outcome/target_type, positive actor_id/target_id; target_id requires target_type. ID types distinguish logical post IDs (`POST`) from version row IDs (`POST_VERSION`). Numeric actor/target IDs are snapshots without foreign keys; deleting the referenced user/resource does not erase event meaning. No names, emails, network addresses, file paths, post contents, credentials or free-form error messages are copied into audit records. Role events contain only a validated ASCII `metadata.role` (at most 255 characters, `ROLE_[A-Z_]+`); other metadata is `{}`. Read mapping projects only that allowlisted metadata key.

Optional offset `from` inclusive / `to` exclusive normalize to UTC. Default to=now, from=effective to minus 90 days. Positive duration at most 90 elapsed days, UTC years 1000–9999, at most microsecond precision. Invalid filters/bounds return JSON 400. Positive offsets must be URL-encoded (`+` as `%2B`). No timezone/DST ambiguity or date-only parsing. Each request uses one read-only repeatable-read transaction for count and page.

Admins export by fetching pages with fixed explicit from/to and filters; split one-year exports into <=90-day windows. Narrow windows when the 10000-page cap is reached. Concurrent writes or retention deletion can shift pages between requests; there is no durable multi-request export snapshot. For a complete consistent operational export, the database operator should use a read-only consistent-snapshot database export. No download-file endpoint or application update/delete route exists.

## Action matrix and atomicity

| Actions | Target | Commit policy |
| --- | --- | --- |
| LOGIN | Proven account USER ID | Same transaction as refresh-session creation; failure closes login |
| PASSWORD_RESET | Proven token account USER ID | Same transaction as password change, token consumption and refresh-session revocation |
| ADMIN_CREATED, ROLE_ADDED, ROLE_REMOVED, USER_DELETED | USER ID | Same transaction as account mutation |
| EMAIL_BANNED, EMAIL_UNBANNED | BANNED_EMAIL row ID, never address/reason | Same transaction as ban/unban |
| POST_APPROVED, POST_REJECTED | POST_VERSION row ID | Same transaction/lock as moderation |
| POST_DELETED | Logical POST ID | Same transaction/lock as logical deletion; repeated deletion emits nothing |
| SCHOOL/DEPARTMENT/SUBJECT_CREATED/UPDATED/DELETED | Taxonomy row ID | Same service transaction; failure or FK rejection rolls event back |
| AVATAR_CHANGED, COVER_CHANGED, COVER_REMOVED | USER ID | Same transaction as media reference; old file removed only after commit; empty cover removal emits nothing |

Successful update commands are recorded even if submitted taxonomy values equal existing values. Generic role addition/removal and promote/demote use the same role actions; role metadata records which role changed. No historical backfill is claimed. Success writes use mandatory transaction propagation: an audit insert failure rolls back the mutation. New/replaced image cleanup retains the prior usable file on rollback. Existing successful API bodies/JWT contracts remain unchanged.

Separately observed HTTP events:

- Failed login/reset: LOGIN or PASSWORD_RESET with DENIED (4xx) or ERROR (5xx), anonymous actor and null target, regardless of whether submitted account/token exists. Successful login/reset is written only by service, never duplicated by filter.
- Password recovery: PASSWORD_RECOVERY with ACCEPTED for generic success, DENIED for 4xx, ERROR for 5xx. Account eligibility/delivery is not exposed; actor and target are null.
- Other 401/403 responses under `/api/v1/admin/` or non-GET `/api/v1/` requests: ACCESS_DENIED, using authenticated principal ID if available, null guessed target. OPTIONS is excluded. Public/read route failures outside this set, arbitrary business validation failures, background SMTP delivery failures, refresh/logout and ordinary content edits are not a comprehensive security history.

Security observation wraps JWT validation and downstream authorization, before security-context cleanup; malformed bearer rejection is observable. Both audit filters use MVC-compatible decoded PathPattern matchers, so encoded prefixes/segments cannot bypass classification or correlation. Correlation filter validates `X-Request-ID` against `[A-Za-z0-9._-]{1,64}` or replaces it with a UUID, sets response header and MDC for all `/api/v1/` requests. Outside HTTP, service calls get a generated UUID. Client correlation IDs are untrusted grouping hints, not unique event IDs or proof of identity. Security results use a separate REQUIRES_NEW transaction with two-second SQL transaction timeout; they survive outer rollback. Persistence failure logs only a static warning and increments `arca.audit.security.write.failures`; it does not replace the original auth/recovery response. Connection acquisition still follows datasource pool timeout; monitor pool pressure and this failure counter.

## One-year retention and database privileges

Approved policy: **365 elapsed days**, admin view/export, manual purge by authorized database operator. No scheduled job or application purge path is installed. Application-level append-only is enforced by absence of update/delete methods/routes. V10 deliberately does not install a trigger requiring elevated MySQL privileges. Database-level enforcement requires separate runtime/migration/operator credentials:

- Runtime account: SELECT and INSERT only on audit_events. No UPDATE, DELETE, DROP, ALTER, TRIGGER or broad schema/global privileges that override these restrictions.
- Migration account: required DDL rights, supplied separately through Spring Flyway credential configuration/deployment tooling; do not leave those credentials on ordinary request-serving connections.
- Retention operator: SELECT/DELETE on audit_events, with controlled export destination and operator access logging.

MySQL grants are additive: a table-level grant does **not** subtract schema-wide UPDATE/DELETE. Inventory `SHOW GRANTS` and replace broad runtime grants with reviewed per-table grants before claiming database-level immutability. This phase does not change production accounts automatically. Do not disable binary-log safeguards or grant SUPER merely to install an audit trigger.

Operator procedure, run daily so manual scheduling does not extend retention indefinitely:

1. Choose and record one UTC cutoff (`current UTC time - INTERVAL 365 DAY`) for this run; inspect counts/date range below it. Obtain any required export/backup according to the same retention policy, protect the destination, and verify completeness before deleting.
2. Purge only older rows in small committed batches. Example SQL template (replace cutoff with the recorded UTC value):

   ```sql
   DELETE FROM audit_events
   WHERE occurred_at < '2025-11-17 00:00:00.000000'
   ORDER BY occurred_at, id
   LIMIT 1000;
   ```

3. Repeat while affected rows remain, observing replication lag/locks and pausing between batches as needed. Never run an unbounded delete or change newer records. Record cutoff, row count, operator and ticket outside the purged audit table.
4. Apply the approved lifecycle to exported copies/backups too; exporting must not silently create an indefinite secondary retention store. Reconcile missed daily runs and alert on oldest retained event age.

## V10 rollout and verification

V1–V9 remain immutable. Rehearse V9→V10 with representative posts/sessions, back up, inspect table/index conflicts, apply V10, then deploy writers/query API. Fresh databases migrate through V10; table starts empty. No user/resource FKs, triggers or content/token backfill. Record deployment start; rolling old nodes do not emit events. Binary rollback retains the table but creates a coverage gap; forward-fix migration/write failures rather than editing applied files.

Check admin query success and ordinary/anonymous denial; known privileged mutation produces exactly one event with expected actor, target, safe correlation and UTC time. A rolled-back mutation must have no success event. Observe audit insert errors, auth-result write-failure metric, query latency, table growth and daily purge status. No production latency SLA is claimed from local tests. `PostSearchQueryPlanTest` exercises 10,000 synthetic events, bounded pages and time/actor indexes, exporting `target/phase-9-query-plans.json`.

Tests cover the action matrix, metadata redaction, no-op exclusion, concurrent user deletion (active-target lock permits only one success/event), encoded MVC paths, approval/deletion/recovery regressions, independent failure writes, mandatory transaction guard, audit-outage/media rollback, admin service/HTTP checks, paging ties/filter intersection, date/ID validation, clean/baseline/V9 migrations and generated OpenAPI parity. Initial local 10,000-event/100-row actor-filtered page measured 5 ms; this is synthetic evidence, not a production SLA. Packaged-JAR smoke with the production MariaDB JDBC driver and disposable MySQL passed V10 startup, real JWT authorization, moderation/delete audit, correlation/redaction and repeated-delete exclusion.
