# Profile Cover Images Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add independent user covers with authorized writes and public binary reads.

**Architecture:** Reuse avatar image validation and containment; consolidate avatar/cover lifecycle inside the existing user service. Lock the user row during replacement/removal, delete old objects only after commit, and remove newly written objects on rollback. Add a nullable storage key and optional response reference.

**Tech Stack:** Java 21, Spring Boot 3.5.5, JPA, Flyway, MySQL 8.4.

**Spec:** `docs/arca-backend-roadmap.md`, Phase 6. Implementation choices delegated by prior “just proceed”; native execution, no commits until requested.

## Global Constraints
- Preserve V1–V6 checksums and avatar contracts; add V7 only.
- JPEG/PNG/WebP, 10 MiB, 25,000,000 decoded pixels; UUID names under `cover-pictures/`.
- Owner/admin PUT and DELETE; public GET; missing reference/object returns 404.
- `cover_picture` is an optional relative storage key, matching avatar discovery semantics; no absolute server paths.
- DELETE returns 200 message and is idempotent for an existing user with no cover.
- Reads use `no-store`, `nosniff`, inline binary content.

## Review Focus
- Failed replacement or outer rollback preserves old bytes and removes new object.
- Stale unrelated profile update cannot resurrect a removed cover (`@DynamicUpdate`).
- Corrupt cover keys cannot read/delete avatars, attachments, or paths outside cover subtree.
- Missing file remains 404; removal can still clear stale reference.
- Authorization precedes service/storage work; ordinary other users cannot mutate covers.

### Task 1: Cover lifecycle and migration
**Files:** `entity/User.java`, `service/UserService.java`, `service/impl/UserServiceImplementation.java`, `db/migration/V7__user_cover_picture.sql`; service, migration and resource authorization tests.
**Interfaces:** `String updateCoverPictureV1(Long, MultipartFile)`, `ProfilePictureContent getCoverPictureV1(Long)`, `void deleteCoverPictureV1(Long)`.
- [x] Add failing lifecycle, rollback, validation and cross-subtree isolation tests; run focused suite RED.
- [x] Add nullable column/entity accessors and consolidate image replacement/read/cleanup helpers parameterized by image kind; implement locked removal.
- [x] Run focused service/migration/integration tests GREEN, including existing avatars.

### Task 2: HTTP contract and discovery
**Files:** `controller/v1/UserController.java`, `config/SecurityConfig.java`, `dto/v1/user/CoverPictureResponse.java`, `dto/v1/user/UserResponse.java`, controller/authorization/OpenAPI tests, `openapi.yaml`.
- [x] Add failing owner/admin/other/anonymous matrix, binary headers and discovery assertions.
- [x] Add cover endpoints, narrow public GET permission, optional `cover_picture` field and response DTO.
- [x] Verify matrix and regenerate checked-in contract consistently with existing parity test.

### Task 3: Verification and operations
**Files:** `docs/phase-6-operations.md`, `README.md`, `API_DOCUMENTATION.md`, this ledger.
- [x] Document V7 rollout, limits, missing-cover/cache/removal semantics, filesystem cleanup assumptions.
- [x] Run `./mvnw --batch-mode verify -Dlogging.level.root=WARN -DtrimStackTrace=true`, OpenAPI parity/export, Redocly lint and `git diff --check`.
- [x] Run disposable MySQL/JAR smoke and independent read-only whole-branch review; record evidence and remaining limitations.

## Evidence
- Initial state: clean `develop` at `1efd4f2`; created `feat/profile-cover-images`.
- Spring transaction documentation confirms afterCommit runs only after successful commit; rollback cleanup belongs in afterCompletion.
- RED: owner/admin/other cover route matrix failed with missing endpoint (500); GREEN after implementation and use of the existing ResponseStatusException handler for authorization.
- Shared lifecycle regressions cover outer rollback for replacement/removal, forced DB flush failure, stale unrelated profile edit, missing/corrupt references, binary headers, traversal upload names, validation failures and avatar/attachment isolation.
- V7 clean/baseline/V6-upgrade preservation checks pass; V6-specific test now explicitly targets V6.
- Task 1: complete; Task 2: complete; Task 3: complete. Final `./mvnw --batch-mode verify -Dlogging.level.root=WARN -DtrimStackTrace=true`: **226 tests, zero failures/errors/skips**, executable JAR built. OpenAPI parity included in suite; export used to update checked-in schema/paths.
- First full run exceeded 120-second tool timeout; second run with a logging override failed existing `PostSearchIntegrationTest.searchObservabilityCorrelatesOutcomesWithoutLoggingQueries` because INFO events were suppressed. Final normal command passed with a 300-second timeout. No application fix required.
- Redocly 2.78.0 minimal lint: valid, **135 existing warnings**, no increase. `git diff --check`: clean.
- Disposable packaged-JAR smoke using MariaDB JDBC driver and MySQL 8.4 passes V7 startup, real JWT owner/other/anonymous access, real multipart parsing, discovery, public binary headers, replacement cleanup, removal and avatar isolation. Temporary app/container/storage cleaned up.
- Independent read-only whole-branch review: no actionable production-code findings; one minor docs status mismatch corrected (excessive decoded pixels return 400; oversized bytes/requests return 413). No deferred findings.
- Known filesystem/process-crash cleanup limits documented in `docs/phase-6-operations.md`.
