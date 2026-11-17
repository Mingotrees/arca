# Admin Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Accurate admin aggregates with forward-only approval timestamps.
**Architecture:** Dedicated controller/service/JdbcTemplate repository and nested response records; seven aggregates in a read-only repeatable-read transaction. V9 adds nullable approval time, set by existing approval transaction.
**Tech Stack:** Java 21, Spring Boot 3.5.5 / Spring Framework 6.2, MySQL 8.4, Flyway.
**Spec:** `docs/superpowers/specs/2026-11-16-phase-8-dashboard-design.md`.

## Global Constraints
- Admin only, snake_case, additive contracts; V1–V8 immutable.
- UTC inclusive/exclusive window, positive duration <=90 days, default trailing 90 days.
- Legacy approval dates stay null; keep approval times on superseding/deletion.
- Seven aggregate queries, no entity graphs/cache; no PII.

## Review Focus
- Rejected newest rows have latest=false: use max version for operational status.
- Version multiplication must not inflate logical creation or status totals.
- Approval activity survives superseding/deletion; pending/rejected never gain approval time.
- Offset normalization and microsecond boundaries; reject malformed/oversized windows.
- Missing/negative sizes must not be presented as known bytes; totals exceed int32 safely.

### Task 1: Approval provenance and dashboard API
**Files:** new V9 migration; Post.java and PostServiceImplementation.java; new AdminDashboardController, AdminDashboardService, AdminDashboardRepository, dto/v1/admin/AdminDashboardResponse; AdminDashboardIntegrationTest and PostMigrationTest.
**Interfaces:** `AdminDashboardService.dashboard(OffsetDateTime from, OffsetDateTime to)` returns AdminDashboardResponse; repository `dashboard(LocalDateTime from, LocalDateTime to, Instant calculatedAt)` returns response.
- [x] Write integration tests for authorization, empty/seeded aggregates, bounds, offsets and approval/version/delete/rollback lifecycle; run RED.
- [x] Add nullable approved_at and UTC first approval write. Implement seven SQL aggregates, service validation/transaction, annotated controller and records; run focused GREEN.
- [x] Test V8→V9 preserving records with null approval time; update clean/latest migration expectations; run migration tests.

### Task 2: Contracts and operations
**Files:** OpenApiContractTest, openapi.yaml, API_DOCUMENTATION.md, README.md, docs/phase-8-operations.md.
- [x] Add dashboard operation/schema parity assertion, export generated OpenAPI and synchronize checked-in artifact.
- [x] Document exact eligibility, current-versus-period distinction, UTC/legacy limits, unknown approval/size metadata, V9 rollout and EXPLAIN/latency evidence.

### Task 3: Release checks
- [x] Run full `./mvnw --batch-mode verify -Dlogging.level.root=WARN -DtrimStackTrace=true`, OpenAPI lint and `git diff --check`.
- [x] Review whole diff against spec; record actual results and outstanding limitations. Commit only if requested.

## Ledger
- Base `99cd931` confirms Phase 7 merged through PR #36. Feature branch `feat/admin-dashboard-aggregates`; HTTPS fetch used after SSH authentication failed.
- User approved dedicated nullable approved_at; inline execution continues under “go ahead” / “continue”.
- Pre-flight: Task 1 response shapes are Task 2 OpenAPI inputs; names and counts fixed by design. Task 3 verifies both; no conflicting interfaces.
- Initial RED: four dashboard integration tests failed on missing endpoint/approved_at. Focused GREEN: four tests after implementation.
- Ruling: current operational status uses highest version, not is_latest_version — rejected newest versions have latest=false and must remain visible in admin totals; differs deliberately from public eligibility.
- Ruling: reject sub-microsecond bounds with 400 rather than let JDBC round them — avoids silently shifting inclusive/exclusive boundaries; clients with nanosecond inputs must reduce precision.
- OpenAPI parity RED on missing dashboard artifact; synchronized typed schemas with explicit snake_case JsonProperty names; parity GREEN.
- Synthetic 10,000-post run: seven executed statements, 25 ms; existing logical-version index serves status lookup; broad activity scan intentional, no extra index justified.
- Extreme-date regression exposed LocalDateTime conversion before range validation (DateTimeException / EpochDay overflow). Validate Instant bounds first; regression RED→GREEN in full suite.
- Task 1: complete; Task 2: complete. Initial full Maven verify: 277 tests, zero failures/errors/skips; executable JAR built. Redocly CLI 2.78.0 minimal lint valid, same 135 pre-existing warnings; whitespace check clean.
- Independent read-only whole-branch review found one important defect: method security was not enabled, making service @PreAuthorize inert even though HTTP admin filters worked. Direct anonymous/non-admin service tests RED (no exception), admin service test GREEN; added @EnableMethodSecurity after checking all existing annotated methods. Both denial regressions now GREEN; full final Maven verify: **280 tests, zero failures/errors/skips**, executable JAR built, OpenAPI parity included.
- Review minor (stale ledger/checkmarks): addressed by this final ledger update; no deferred minor findings.
- Final: Ruling: production latency/index sufficiency remains deployment-measured, not a claimed SLA — seven-query synthetic evidence supports current implementation; larger production distributions may require additional tuning.
- Final: Ruling: preserve existing creation-clock semantics instead of retrospectively rewriting data — legacy offsets cannot be recovered; creation-window accuracy depends on the deployment's original clock convention. New approved_at is explicitly UTC.
- Final: Ruling: retain configured repeatable-read transaction without adding concurrency orchestration infrastructure — standard MySQL consistent-read semantics cover the seven shared-datasource queries; concurrent snapshot behavior has no dedicated regression test.
- Task 3: complete. Changes remain uncommitted on feat/admin-dashboard-aggregates; V9 has only been applied to disposable test databases, not production.
