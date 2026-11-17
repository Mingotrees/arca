# Audit Events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for inline execution, task by task.

**Goal:** Append-only, redacted audit trail with admin paging and one-year operator-managed retention.
**Architecture:** JDBC insert/query store, mandatory-transaction success writer, REQUIRES_NEW security-result writer; explicit calls in mutation services. Servlet correlation plus security-chain observation capture request denials independently of rollback.
**Tech Stack:** Java 21, Spring Boot 3.5.5, Spring Security method authorization, JdbcTemplate, MySQL 8.4/Flyway.
**Spec:** `docs/superpowers/specs/2026-11-17-phase-9-audit-design.md`.

## Global Constraints
- V10 only; V1–V9 immutable. Numeric actors survive deletion, no FK or credential/PII metadata.
- Admin-only bounded query; snake_case and existing responses preserved.
- Success event shares mutation transaction and fails closed; separate security-result failures do not change response.
- No application update/delete path. Runtime SELECT/INSERT-only grants reject mutation SQL; DELETE reserved operationally for 365-day retention.

## Review Focus
- Login/reset cannot leak submitted email/password/token or duplicate successes in HTTP filter.
- Audit insert failure must roll back privileged mutation and media replacement, not just report an error afterward.
- Request correlation and actor must survive filter cleanup but never identify a guessed anonymous target.
- Idempotent delete/no-op requests must not fabricate mutation events.
- Paging ties and count query must match all bound filters; numeric IDs and date bounds validate before SQL.

### Task 1 — Audit storage and admin query
**Files:** V10 migration; service/audit/AuditAction, AuditContext, AuditService, SecurityAuditWriter; repository/AuditEventRepository; dto/v1/admin/AuditEventResponse/AuditEventPage; service/AuditQueryService; controller/v1/AdminAuditController; AuditEventsIntegrationTest, PostMigrationTest.
**Interfaces:** `AuditService.success(AuditAction action, Long targetId)` derives actor; overload with actor for proven login/reset. `SecurityAuditWriter.record(AuditAction action, String outcome, Long actorId, String correlation)` independent transaction. `AuditQueryService.query(Integer page,Integer size,String action,String outcome,String targetType,Long actorId,Long targetId,OffsetDateTime from,OffsetDateTime to)`.
- [x] Add RED integration tests for admin API, filtering/paging/redaction, transaction/rollback/outage behavior and absent mutation routes.
- [x] Implement V10 store, writers, allowlisted query and annotated controller/DTO; run focused GREEN.
- [x] Test clean/V9 migration preserving posts/sessions and actor retention; document deployment grant checks instead of privileged trigger.

### Task 2 — Mutation and security integration
**Files:** AuthSessionService, recovery/PasswordRecoveryService; Post/User/BannedEmail/School/Department/Subject service implementations; SecurityConfig, new AuditSecurityFilter and AuditCorrelationFilter; existing unit/integration tests.
- [x] Add RED fixtures for audited action matrix, correlation, no-op exclusion, authorization failures and generic recovery outcomes.
- [x] Add explicit success writes in existing transactions, transactional taxonomy mutations and safe independent observation filter; run focused GREEN. Update constructor mocks where necessary.
- [x] Prove audit storage outage fails closed for mutations, rolls back file reference/replacement, while security denial/recovery response remains intact.

### Task 3 — Contracts and release
**Files:** OpenApiContractTest/openapi.yaml, API_DOCUMENTATION.md, README.md, docs/phase-9-operations.md; query-plan test and this ledger.
- [x] Export/sync schemas and add parity tests; capture indexed query plans on representative corpus.
- [x] Document action matrix, failure policy, identity/retention/export/purge/grants and rollout.
- [x] Run full Maven verify, pinned Redocly minimal lint, whitespace check; independent whole-branch review and regression fixes.

## Ledger
- Base de5bbd7 includes merged Phase 8 (PR #37); clean feat/admin-audit-events branch.
- User approved one year/admin export/operator purge, then transaction-aware option 1 and explicitly requested proceeding. Inline implementation continues; no commit until requested.
- Pre-flight: Task 1 writer signatures/enum are consumed by Task 2; query DTO names feed Task 3 OpenAPI. No interface conflict.
- Initial RED: missing audit_events table; storage/query and role transaction tests GREEN after V10/writers/API.
- Ruling: no privileged UPDATE trigger — MySQL binary logging rejects trigger creation with ordinary migration credentials (1419); application has no update/delete API, runtime SELECT/INSERT-only grants enforce database immutability. Deployment must separate DDL/runtime credentials; no server safeguard is weakened.
- Ruling: allow only validated role identifier in metadata — empty metadata cannot identify which privilege changed; never permit arbitrary caller maps, secrets, content or addresses.
- Failed-login/denial RED: zero events; security-chain filter GREEN. Invalid-JWT regression RED because JWT returned before observation; moved observer before JwtFilter and resolve actor on exit; 52 focused tests GREEN.
- Mutation matrix exposed pre-existing taxonomy lazy access outside transaction; planned service transactions fix it. Test aliases changed to mutable list matching JSON deserialization, avoiding an unrelated immutable-list merge artifact.
- Role metadata regression RED and OpenAPI parity RED recorded; role metadata and explicit integer parameter schemas implemented, parity artifact synchronized; initial full Maven verify GREEN: 290 tests, zero failures/errors/skips, executable JAR built.
- Task 1: complete; Task 2: complete. Corpus 10,000 events, 100-row actor-filtered page observed 5 ms; time/actor index plans captured. Redocly 2.78.0 minimal lint valid with unchanged 135 baseline warnings, whitespace clean.
- Packaged-JAR/production MariaDB JDBC/disposable MySQL smoke passed V10, real JWT owner/admin/invalid-token checks, moderation/delete audit, correlation/redaction and no-op deletion. Initial smoke setup failures were missing prod-profile datasource/JWT settings and SHA2 auth cache setup; corrected test-only environment, no production config changed. Temporary application/container/files removed.
- Independent review found two important issues: raw URI matching missed encoded MVC routes; unlocked user deletion could emit duplicate USER_DELETED events. Encoded correlation regression RED; concurrent deletion regression RED (2 successes). Fixes use MVC-compatible PathPatternRequestMatcher and active-user row lock/delete; all 9 audit integration tests GREEN. Initial repository-spy synchronization used callRealMethod on a JDK proxy; corrected test to delegate its original answer, preserving real database behavior.
- Final: Ruling: database-level privilege enforcement remains deployment work — table grants are additive and runtime broad privileges must be removed before claiming database immutability; cost if omitted is direct-SQL update/delete capability outside application APIs.
- Final: Ruling: operator must execute daily 365-day purge and manage exports/backups — no automatic retention job was approved; cost if omitted is retention beyond policy or inconsistent exports.
- Final: Ruling: production latency/availability must be measured under deployment load — synthetic indexed queries and two-second security SQL timeout do not establish pool-exhaustion behavior; cost if omitted is unknown production headroom.
- Final: fixed encoded-path auditing bypass — encodedMvcPathsKeepSecurityClassificationAndCorrelation RED→GREEN; shared MVC-compatible matchers classify encoded recovery/login/admin routes and preserve correlation headers.
- Final: fixed duplicate concurrent user-deletion provenance — concurrentUserDeletionCommitsOnlyOneTransitionAndOneEvent RED→GREEN; active-user FOR UPDATE serializes transition and second caller receives existing already-deleted error.
- Final full `./mvnw --batch-mode verify -Dlogging.level.root=WARN -DtrimStackTrace=true`: **292 tests, zero failures/errors/skips**, executable JAR built; OpenAPI parity included. No deferred review findings. Task 3: complete. Changes remain uncommitted on feat/admin-audit-events; V10, grants and retention procedure have not been applied to production.
