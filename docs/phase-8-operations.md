# Phase 8 — Admin dashboard aggregates

## API

`GET /api/v1/admin/dashboard?from=2026-11-01T00:00:00Z&to=2026-11-16T00:00:00Z`

Requires an admin bearer JWT. Anonymous callers receive JSON 401; other authenticated roles receive JSON 403. Service-level admin authorization also protects non-controller callers. Returns aggregate numbers only, without user identities, emails, credentials, file paths or content.

Optional `from`/`to` accept ISO offset date-times, including `Z`, normalized to UTC. URL-encode positive offsets (`+` as `%2B`). Window is **inclusive from, exclusive to**; positive elapsed duration at most **90 days**. Default `to` is calculation time and default `from` is effective `to` minus 90 days. Supplying only `from` uses calculation time as `to`. Future windows are allowed. UTC years must lie within 1000–9999; precision is at most six fractional digits (microseconds). Invalid bounds/format/precision return JSON 400. No date-only interpretation or server-local timezone/DST rules apply.

Top-level fields: `calculated_at`, `timezone` (`UTC`), effective `from`/`to`, `users`, `posts`, `moderation`, `engagement`. Counts and byte totals are signed int64; empty scalar aggregates are zero, empty grouped maps are `{}`. Group keys preserve stored role/status spelling and sort lexically; absent groups have no key.

## Definitions

Current-state aggregates are **not filtered by the requested period**:

- `users.active`: `is_deleted=false` or legacy null. `users.deleted`: true. `users.by_role`: distinct active users per non-null role; one user can count in multiple roles. Users with no role remain in active total.
- `posts.active`, `posts.deleted`, `posts.by_status`: one **highest-version row** per logical `post_id`. Deleted logical posts count only in deleted total. Active status includes newest rejected submissions even though rejection sets `is_latest_version=false`. An older approved version can therefore remain publicly visible while operational current status is pending/rejected. Do not interpret this as the public/search count.
- `moderation.pending`: active current logical posts whose newest version is `PENDING_APPROVAL`, same definition as status totals.
- `engagement`: only approved/latest/non-deleted post version rows, matching public profile-statistics eligibility. Retained votes/saves by deleted users and posts by deleted authors remain counted. `upvotes`/`downvotes` count each stored vote; `vault_saves` counts each saved entry. Historical/deleted/private post engagement is excluded.
- `attachments`: stored file rows on eligible posts. `attachment_bytes`: sum of known nonnegative `file_size`; `attachments_without_known_size`: null/negative metadata sizes. These are reference metadata totals, **not physical storage usage**. Historical/deleted retained files, avatars/covers, missing objects and shared/copied paths make physical usage different.

Period activity uses `[from,to)`:

- `posts.created_in_period`: version-1 rows created in window, one per logical post, including subsequently deleted posts. Later revisions do not add creations.
- `posts.approved_versions_in_period`: version rows whose `approved_at` lies in window, including subsequently superseded/deleted rows. Multiple approved revisions of one logical post count separately. This is approval-version activity, not distinct currently published posts.
- `posts.legacy_approved_versions_without_date`: all-time count of approved/superseded rows with null `approved_at`, including deleted rows. Their dates are unknown; they are never allocated to a period. Mixed-version deployments can add more unknown-date approvals until every node is upgraded.

## Approval timestamp and clock limitations

V9 adds nullable `posts.approved_at DATETIME(6)` without a backfill. A successful pending→approved transition records UTC in the same transaction and under the existing logical-post lock. Rejection/new revision leaves it null. Superseding and soft deletion retain it; transaction rollback rolls it back. Existing approval responses and public post DTOs are unchanged.

Exact approval-date coverage starts as upgraded nodes handle approvals. There is no fabricated historical activity and no audit actor/action log in this phase. Existing post `created_at` was written using the server's local clock; the dashboard compares stored values as UTC for its reporting convention, but cannot reconstruct historical timezone offsets. Verify deployment clock configuration before interpreting old creation windows; changing the JVM timezone does not repair old data. This limitation does not affect new `approved_at` writes, which explicitly use UTC.

## Queries and performance

Seven SQL aggregate queries execute in one read-only repeatable-read transaction: users, roles, current logical statuses, activity, files, votes and vaults. No entity graphs, per-row queries or cache. `calculated_at` marks calculation start; the database snapshot starts with the first consistent read, not an event-history watermark.

Synthetic MySQL 8.4 corpus: 10,000 posts, 100 users, 20,000 subject tags, 1,000 votes, 200 files and populated links. Initial observed aggregate time **25 ms**, seven real SQL statements. Query plans exported by `PostSearchQueryPlanTest` to `target/phase-8-query-plans.json`. Current-status lookup uses existing `uk_posts_logical_version` for correlated max-version lookup; users use `idx_deleted`; role/engagement joins use primary/FK indexes. Broad status/activity totals intentionally scan post rows. No additional index justified by this measurement; production volume/distribution can differ. No production latency SLA has been agreed; measure authenticated endpoint p95 on representative deployment load before setting one.

## V9 rollout

1. Back up and rehearse V8→V9 on a restored copy. Existing V1–V8 checksums remain unchanged. Inventory production MySQL version/table size and budget ALTER metadata locks; do not assume instant DDL on every deployment.
2. Apply V9 before deploying binaries with `ddl-auto=validate`. It adds one nullable column, preserving posts/links/media/sessions. Clean installations apply V1–V9.
3. Upgrade all nodes; approvals from old binaries leave unknown dates. Record when all approval writers are upgraded. Existing JWTs and routes remain compatible.
4. Verify admin 200, regular-user 403, anonymous 401; approve a disposable pending post and check UTC `approved_at`, window inclusion, and preserved timestamp after a revision supersedes it. Compare counts with repository SQL using a consistent snapshot.
5. Observe query latency, metadata-size unknown counts and legacy approval coverage. Existing request-correlation/HTTP metrics cover the route. Do not log bearer tokens or request content.
6. Binary rollback retains V9 column/data; old nodes resume writing null dates. Use a reviewed forward migration for fixes, never remove the column or rewrite applied migrations.

Automated verification covers clean/baselined/V8 migration, legacy-null preservation, admin security, empty and mixed aggregates, version/deletion behavior, approval atomicity, UTC/offset/DST/boundary validation, int64 byte totals, seven-query corpus plans and generated/checked-in OpenAPI parity.
