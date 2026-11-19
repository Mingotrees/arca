# Homepage Modules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for inline execution task by task.

**Goal:** Independently flagged announcements and timestamp-aware trending with audited admin writes.
**Architecture:** JDBC repositories/services and typed DTOs/controllers; additive V11 schema; nullable vote UTC change time written by existing vote service. On-demand bounded ranking with deterministic tie-breakers.
**Tech Stack:** Java21, Spring Boot3.5.5/MVC/Security, JdbcTemplate, MySQL8.4/Flyway.
**Spec:** `docs/superpowers/specs/2026-11-17-phase-10-homepage-design.md`.

## Global constraints
- Preserve V1–V10, JWT roles, snake_case and existing successful routes/DTOs.
- Flags independent, default false; disabled module returns 404 to authorized callers.
- Global announcements only; no URL fetch or arbitrary rich HTML. Mutation/audit transaction atomic.
- Nullable legacy vote/approval dates never fabricated. Exact UTC window/score/limits from spec.
- Deployment work follows feature verification; do not mutate production during implementation.

## Review focus
- Schedule boundary/null semantics and full-replacement defaults must agree across request, SQL and docs.
- Concurrent edit/delete cannot expose deleted rows or duplicate deletion audit.
- Historical/unknown/future dates and inconsistent latest flags cannot leak or duplicate trending posts.
- Vote switch/toggle/removal must preserve old contracts while changing current recent counts correctly.
- Each flag must work independently without changing ordinary post reads/search.

### Task 1 — Announcements, flags and audit
**Files:** V11 migration; HomepageProperties; announcement request/response/page DTOs; AnnouncementRepository/AnnouncementService; AdminAnnouncementController/AnnouncementController; AuditAction/AdminAuditController; HomepageIntegrationTest/feature-flag tests.
**Interfaces:** service create/replace/get/list/delete/active, admin service guard; repository row locks and JDBC generated keys; announcement audit actions target ANNOUNCEMENT.
- [x] Write RED MVC/integration tests for CRUD/scheduling/privacy/auth/rollback and independent disabled routes.
- [x] Implement schema, properties, bounded DTO/controller/service/repository and audit integration; run focused GREEN.

### Task 2 — Timestamped votes and deterministic trending
**Files:** Vote.java/VoteServiceImplementation; TrendingRepository/TrendingService/TrendingController and response DTOs; HomepageIntegrationTest; migration/query-plan tests.
**Interfaces:** repository ranks bounded summaries at one UTC instant, service validates window/limit and flags; vote setter stores UTC changed_at on new/switched votes only.
- [x] Write RED ranking/window/visibility/dedup fixtures and vote lifecycle checks.
- [x] Implement spec score and nullable vote time; run focused GREEN.
- [x] Test V10→V11 preserving legacy vote/approval data and capture corpus plans; existing media migrations/regressions remain covered by full suite.

### Task 3 — Contracts, operations and release
**Files:** openapi.yaml/OpenApiContractTest, API_DOCUMENTATION.md, README.md, docs/phase-10-operations.md and this ledger.
- [x] Synchronize generated contract, document limits/clock/flag/rollout behavior, run OpenAPI parity and lint.
- [x] Run full Maven verify, inspect query measurements and independent whole-branch review; address important findings with regression evidence.
- [x] Record final results and begin deployment inventory after Phase 10 is complete; resolve target/access/backup details before production changes.

## Ledger
- Base c968a98 includes merged Phase9 PR38; clean feat/optional-announcements-and-trending branch.
- User confirmed Phase10, timestamped vote option, and “go”; inline execution continues. Deployment queued after feature work, commits/pushes only on request.
- Pre-flight: announcement audit enums affect checked-in audit OpenAPI; V11 vote column feeds ranking. All names/limits fixed by spec; no interface conflict.
- Initial RED: announcement table/new endpoints absent. Initial five CRUD/ranking/default-off tests GREEN after implementation.
- Ruling: determine highest current approved/latest/non-deleted revision before applying date eligibility — unknown/future dates on a newer flagged revision must suppress old flagged rows; cost is excluding a logical post with inconsistent newest approval metadata rather than resurfacing history.
- Malformed Unicode regression RED (201 accepted unpaired surrogate), then text validation rejects surrogate code points before JDBC. Request numeric/boolean null unions explicitly document defaulting semantics.
- Expanded module tests: all four flag combinations, schedule/priority/visibility, switch/toggle/removal, exact vote-window bounds, no-fetch, audit rollback, direct admin guard and concurrent deletion GREEN. OpenAPI parity RED on missing homepage artifact; generated YAML/JSON exported and checked-in paths/schemas/audit enums synchronized.
- Query corpus: 10,000 posts/votes and 1,000 announcements; observed41ms trending and2ms active announcements, one statement each. EXPLAIN references idx_posts_trending as possible key but selects idx_status for the all-recent candidate corpus; recent vote aggregation selects idx_votes_trending_changed. Plans exported.
- Final full Maven verify: **306 tests, zero failures/errors/skips**, executable JAR built; generated OpenAPI parity included. Pinned Redocly2.78.0 minimal lint valid with unchanged135 baseline warnings; whitespace check clean.
- Packaged-JAR smoke with production MariaDB JDBC/disposable MySQL passed V11, generated announcement keys, real JWT roles, CRUD/audit, vote/trending/toggle, restart with both flagsfalse, and legacy search. Temporary application/container/files removed.
- Independent read-only review: no blocking correctness/security/compatibility findings. Final: minor (deferred): HomepageQueryPlanTest checks the index name anywhere in EXPLAIN, including possible_keys; strengthen selected-access-path/selective-corpus assertions later. Current selected key alone is not a demonstrated performance defect; documentation now records actual selected keys.
- Task1: complete; Task2: complete; Task3: complete. Deployment inventory started after verification; changes remain uncommitted. No production migration/configuration has been applied.
- Deployment discovery: existing DCISM workflow deploys master/manual dispatch; six required secret names are configured. Latest successful run43554738112 (2026-11-14) deployed master d1ed396. GitHub compare reports develop16 commits ahead/1 behind master. Public production OpenAPI currently does not advertise search/recovery/covers/dashboard/audit/trending; actual database version/history remains unverified.
- Subsequent approved SSH/tunnel preflight: ver.dcism.org:22072, backend /data/users/s22101440/arca-backend. Explicit read-only transaction confirmed MariaDB11.8.6, schema s22101440_arca, baseline1+V2/V3; pending V4–V11. Search collation alias is recognized, token limits3/84; active post integrity check passed. Runtime grants are schema-wide ALL PRIVILEGES, so restricted audit grants still require administrator provisioning. No live DDL/configuration/data change performed; isolated MariaDB rehearsal and verified backup/restore remain next deployment steps. Details in docs/deployment-roadmap-release.md.
- User approved backup maintenance on2026-11-19. Existing backend gracefully paused/restarted in10.83s; original configuration/JAR retained and public health200. Matched database/media/JAR/config backup captured server-side and copied off-server with verified SHA-256, private0700/0600 permissions, outside Git.
- Actual backup restored on isolated MariaDB11.8.6: all16 original table counts matched; V4–V11 applied; original projected legacy data/Flyway rows preserved. Upgraded runtime passed auth, search, dashboard, audit, actual legacy-media bytes, post moderation/deletion, vote/trending, profile stats and announcements. Clone-only runtime/migrator separation passed startup and denied audit UPDATE/DELETE. Receipts recorded in both private backup locations; disposable containers removed. Closing production check: V3, backend PID2544989, health200. Live migrations/grants/release remain pending.
- User authorized Phase10 commit/push/PR and continuation of account provisioning on2026-11-19. Fresh Maven verify GREEN:306 tests, zero failures/errors/skips. SSH follow-up to port22072 timed out before authentication; administrator provisioning request prepared in docs/dcism-database-account-request.md. No production account/grant/migration change attempted during this follow-up.
