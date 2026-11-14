# Phase 1: soft deletion and migration rollout

## Behavior and retention

- `DELETE /api/v1/posts/{rowId}` resolves database row `id` to logical `post_id`. Owner/admin deletion marks every version in one transaction and returns 204, including authorized repeats. Repeats preserve original provenance. Other users get 403 for active posts and 404 for deleted posts; missing rows get 404.
- Deleted content is absent from lists, history, queues, related file/vote/vault APIs, and mutation paths, including admin reads. History preserves its legacy list contract and returns an empty list when no active versions remain.
- All post versions, attachment rows and bytes, votes, and vault rows remain stored. No restore/purge API or physical file cleanup is introduced. Retention is indefinite until a separate retention decision; backups contain retained data too.
- `deleted_by` is a scalar actor ID without a cascading foreign key. User soft deletion or future hard deletion must not erase it. Existing post/file/vote/vault foreign keys retain their non-cascading behavior. There is no new user hard-delete behavior.
- Existing rows use `is_deleted=false`; deletion timestamps/actor are null until deletion. Deletion fields are not added to normal `PostResponse`.
- Every existing-post writer acquires the retained `post_identities` parent-row lock before its target-version lock. Version creation, moderation, uploads, votes, and vault mutations participate. Do not physically purge that anchor or introduce writers that bypass it.
- New logical IDs use database AUTO_INCREMENT, not `MAX(post_id)+1`. They remain integers but are not promised to be contiguous; rolled-back allocations may leave gaps. Existing logical/row IDs remain unchanged. Version allocation uses a current locking read after acquiring the identity lock, avoiding stale repeatable-read snapshots.
- A unique `(post_id, version)` constraint and `(post_id, user_id)` foreign key to the identity/owner prevent duplicate versions and mixed-owner logical groups. The identity's scalar `owner_id` has no cascading user reference; identity rows remain retained after deletion. Ordinary result queries have explicit deletion predicates; native queries must maintain them too.

## Database ownership

Flyway owns schema changes. Hibernate uses `validate`, including the existing dev profile. Do not override it with `update` in deployment environment variables. Flyway defaults `baseline-on-migrate=false` and `clean-disabled=true`.

- `V1__legacy_schema.sql`: clean-install schema derived from pre-roadmap entity mappings. It is a **reference baseline**, not evidence that any deployed schema matches it.
- `V2__post_soft_deletion.sql`: additive columns and `(is_deleted, is_latest_version, status, updated_at, id)` index.
- `V3__Logical_post_identity.java`: rejects ambiguous legacy identities, backfills retained identity/owner rows, and adds version uniqueness and owner consistency constraints. This Java migration lives under `src/main/java/db/migration` and must be compiled before Maven migration commands.
- Never edit a migration after deployment; append a corrective migration. MySQL DDL is not transactionally rolled back. A failed migration may leave partial DDL; inspect the schema before any repair/retry.

## Required inventory before the first production migration

Record the actual database product/version, schema name, JDBC URL scheme/driver, collation, storage engines, row counts, post table size, free disk space, indexes, constraints, and `SHOW CREATE TABLE` output for every mapped table. Current local Compose uses MySQL 8.4; the application keeps its MariaDB JDBC driver. Verify the real deployment rather than inferring it from Compose.

Compare each environment to V1 and the current entity mappings. Reconcile differences with a reviewed, environment-specific forward-only preparation plan **before assigning baseline 1**. Do not run V1 over populated tables or use blanket `baseline-on-migrate=true` to bypass drift. No deployed schema was inspected or modified during implementation.

Inventory duplicate `(post_id, version)` groups, logical IDs shared by multiple owners, and non-positive logical IDs. V3 deliberately stops before creating identity tables if these exist; it never guesses ownership or rewrites IDs. Reconciliation requires a reviewed data/contract decision before rollout. Stop all writers during the backfill and constraint installation.

For large `posts` tables, rehearse V2 and V3 on a restored snapshot and record migration duration, metadata-lock time, disk use, backfill duration, and index/constraint build impact. The ALTER operations are suitable only when the measured maintenance window permits them. If an online schema-change tool or separate index operation is needed, agree that deployment strategy before applying migrations anywhere; never edit an applied migration. Arrange and verify database/media backups and a restore rehearsal.

## Explicit baseline and rollout

Run tooling from the repository root at the release revision, with Java 21. Maven's Flyway plugin uses the same managed Flyway version and migration directory as the application. Supply `FLYWAY_URL`, `FLYWAY_USER`, and `FLYWAY_PASSWORD` through the deployment secret environment; the Maven plugin does not load the application's `.env`. Use a driver-compatible JDBC URL and avoid credentials in command-line arguments or logs.

1. Rehearse clean installation and snapshot upgrade. Run `./mvnw verify` with Docker available; tests use disposable MySQL 8.4 containers, never the configured application database.
2. Complete inventory, backup, and schema reconciliation above. Drain traffic and stop every old application instance. Old binaries do not filter deleted posts or honor the new logical-post lock. Mixed-version serving is unsafe once deletions begin.
3. On an existing unversioned schema, explicitly mark the reviewed legacy baseline:

   ```sh
   ./mvnw compile flyway:info
   ./mvnw flyway:baseline
   ./mvnw flyway:info
   ```

   Confirm baseline version **1**, expected database/schema, and pending V2/V3. `baseline` records history; it does not execute V1. On an empty database, skip baseline entirely so `migrate` executes V1–V3.

4. Apply and inspect migrations while traffic remains drained:

   ```sh
   ./mvnw compile flyway:migrate
   ./mvnw flyway:validate
   ./mvnw flyway:info
   ```

   Confirm version 3, unchanged legacy post/version counts, matching identity/owner rows, default false deletion state, null provenance, and new indexes/constraints. Flyway startup also migrates pending changes, but first production adoption should use this explicit maintenance procedure.

5. Start only Phase-1-aware binaries. Hibernate validation must succeed. Check `/v3/api-docs` and a JWT-authenticated read; verify a controlled test post deletion returns 204 and blocks its older version, history, file, vote, and vault access. Confirm unrelated posts remain accessible. Then reopen traffic.
6. Watch HTTP 4xx/5xx rates, database lock waits/deadlocks, migration logs, query latency, disk usage, and retained-file growth. Deletion provenance is in `posts`, not a fabricated audit history; durable audit events remain Phase 9.

## Request observability

The deletion request filter runs before security and emits `post_delete outcome=... status=... correlation_id=... duration_ms=...` for successes, authorization failures, and errors. It does not log raw paths, bodies, credentials, or arbitrary headers. Optional `X-Request-ID` values must match `[A-Za-z0-9._-]{1,64}`; missing/unsafe values become UUIDs. The safe ID is returned in the response header and is available to browser clients through CORS.

Actuator/Micrometer records the `arca.post.deletion` timer with bounded `status`/`outcome` tags, plus standard HTTP server metrics. Query `/actuator/metrics/arca.post.deletion` with an admin JWT for counts, total time, and maximum duration; successive count deltas provide rates. `/actuator/**` requires `ROLE_ADMIN`; only health and metrics are exposed by default. Default meters are process-local and reset at restart. Configure an approved registry/exporter separately when durable monitoring is required; correlation IDs and resource IDs are never metric tags.

The deploy workflow runs tests and OpenAPI validation before upload. **For the first rollout, use the maintenance procedure above rather than an unattended push.** On health-check failure, it stops the new process and keeps the previous JAR only for diagnosis; it no longer automatically restores an old binary. Restoring a pre-Phase-1 binary can reveal retained deleted content. Do not reopen traffic on one. A failed rollout requires a Phase-1-aware forward fix, or keeping traffic drained until operators have restored a verified consistent backup under the approved recovery procedure.

## Forward fix and failure handling

- Failed baseline/validation: keep traffic drained; verify target and reconcile drift. Never automatically clean a database or baseline an unexpected schema.
- Failed V2/V3: inspect history and actual columns/tables/indexes/constraints. MySQL can retain DDL. Use a reviewed corrective procedure/migration and repair history only after confirming physical schema; do not blindly rerun partially applied DDL. A V3 preflight rejection leaves post data unchanged; reconcile the ambiguity before any history repair/retry.
- Application regression after migration: retain additive schema and deploy a compatible forward fix. Do not drop deletion columns, reset flags, or fall back to a binary that ignores deletion.
- Database failure during deletion rolls back every version's deletion metadata. A retry by owner/admin is safe. Files are never removed by the delete request.
- Future media cleanup must reconcile references with database state, follow an approved retention window, and run after successful commits. It must not race active uploads or delete files belonging to retained versions.

## Verification coverage

`PostMigrationTest` covers clean bootstrap, representative legacy versions, refusal to silently baseline a nonempty schema, explicit baseline/upgrade, ambiguous-identity rejection, and repeated migration no-op. Context tests validate the migrated schema against Hibernate mappings. `PostDeletionIntegrationTest` exercises real MySQL transactions, owner/admin/other/anonymous access, repeat provenance, rollback, every list/read/write boundary, file retention, concurrent creation across owners, deletion/version/moderation races, correlated logs, metrics, and monitoring authorization. `OpenApiContractTest` compares checked-in deletion and related-resource operations with generated springdoc output and verifies legacy route/operation preservation and unchanged PostResponse property names.

These tests are not a substitute for a restored production snapshot rehearsal or measured large-table migration timing.

Observed tooling warnings: the Boot-managed Flyway 11.7.2 MySQL module warns that MySQL 8.4 is newer than its tested support level; migrations and Hibernate validation succeeded on local MySQL 8.4. The MariaDB JDBC tooling check also emitted a non-fatal performance-schema diagnostic permission warning with the restricted test account. Record these during production rehearsal; do not grant broad diagnostic privileges just to suppress a warning.
