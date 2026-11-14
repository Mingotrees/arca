# Phase 2 Resource Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce consistent post visibility and safe media handling while preserving successful `/api/v1` contracts.

**Architecture:** Keep controller → service → repository. Introduce one post access policy, shared upload validation and canonical-path storage helpers; preserve logical-post transaction locks. Authenticate resource reads at the service boundary using the existing Spring Security principal.

**Tech Stack:** Java 21, Spring Boot 3.5.5, Spring MVC/Security/JPA, MySQL 8.4, JUnit, Testcontainers.

**Spec:** `docs/arca-backend-roadmap.md`, Phase 2 and cross-cutting delivery requirements. User delegated design and execution with “just proceed”.

## Global Constraints

- Preserve routes, successful response shapes, snake_case, row-ID/logical-ID meaning, JWT claims and refresh behavior.
- No schema changes, commits, or deployment against existing databases.
- Authenticated callers can read active APPROVED posts; owners/admins can also read other active statuses. Missing/deleted/inaccessible posts return generic 404. Forbidden modification of a visible post returns 403.
- History and vault lists filter inaccessible versions; deleted history remains an empty array. Owners/admins retain active history access.
- Votes require APPROVED status even for owner/admin; vault entries require readable parent posts and remain scoped to their saving user.
- Keep JPEG/PNG/PDF attachments (three per version), JPEG/PNG/WebP avatars, 10 MiB per file and total payload. Bound decoded images at 25 million pixels. No new formats, scanning service, throttling, or token migration.
- Keep explicit development origins; reject blank, wildcard, malformed, or insecure non-loopback production CORS origins at startup. Require a valid Base64 JWT secret decoding to at least 32 bytes at startup.

## Review Focus

- Pending/rejected/superseded versions must not leak through history, attachment, vote counts, or saved entries.
- A supplied legacy user_id must never alter ownership; guessed vault IDs must not expose another user's entries.
- Symlinked storage directories/files must not escape canonical upload root, including public avatars.
- Failed media replacement/transaction commit must retain old avatar and remove newly written files.
- Spoofed image signatures, oversized content, malformed names and persisted legacy filenames must not bypass validation or inject download headers.

---

### Task 1: Shared authorization

**Files:** Create `service/PostAccessPolicy.java`; modify post/file/vote/vault service implementations; add `ResourceAuthorizationIntegrationTest.java`; update request DTO deprecation annotations.

**Interfaces:** `PostAccessPolicy.requireReadable(Post)`, `requireReadable(Post, Long, boolean)`, `canRead(Post)`, `requireMutable(Post, Long, boolean)`, `requireDeletable(Post, Long, boolean)`. Current-actor reads use `UserPrincipal` from SecurityContext; explicit actor methods retain trusted controller parameters.

- [x] Add MySQL/MVC matrix tests for unrelated/owner/admin/anonymous across approved, pending, rejected, superseded, deleted posts, history, files, votes, vault reads/writes; assert denied mutations leave rows unchanged and spoofed user_id is ignored.
- [x] Run focused test and observe existing visibility leaks.
- [x] Implement policy and apply it at every parent-post boundary, retaining writer locks and idempotent authorized deletion; eliminate exception wrapping that hides authorization statuses.
- [x] Run matrix and existing deletion/service/controller regressions; preserve authorized behavior.

### Task 2: Upload and storage hardening

**Files:** Create focused `service/media/UploadValidator.java` and `MediaStorage.java`; modify file/user services and controllers; add media tests and transaction integration coverage.

**Interfaces:** validator returns validated bytes, safe display name, extension and MIME; storage provides canonical contained destinations and existing-file resolution. File writes register rollback cleanup; avatar replacement removes old object only after commit.

- [x] Add tests for byte limits, full-batch rejection, empty/unsupported/spoofed/malformed uploads, decoded pixel bounds, traversal display names, symlink escape, safe download headers, avatar rollback and replacement.
- [x] Run focused tests to observe missing controls.
- [x] Implement bounded reads, extension/content/MIME validation, generated names, canonical containment and transaction-aware media lifecycle. Preserve WebP avatar compatibility with decoded image validation. Emit no-store/nosniff and safe Content-Disposition on downloads.
- [x] Run media, authorization and deletion regressions.

### Task 3: Configuration and errors

**Files:** `JWTService.java`, `SecurityConfig.java`, `GlobalExceptionHandler.java`, application configuration, vote/vault DTOs/controllers; add configuration/error tests.

**Interfaces:** JWT startup initialization validates and caches existing signing key; CORS source rejects unsafe values before serving requests. MVC and filter errors share timestamp/status/error/message fields while retaining legacy error identifiers.

- [x] Test startup rejection of missing/weak/malformed keys and unsafe origins; test accepted explicit development/production origins and unchanged signed JWT validation.
- [x] Test invalid vote/request payloads and JSON validation/security envelopes, plus service-level 413 handling.
- [x] Implement controls, explicit multipart defaults across profiles, and focused request validation.
- [x] Run focused configuration and request/security regressions.

### Task 4: Contracts, operations and verification

**Files:** controller/DTO annotations, `openapi.yaml`, `API_DOCUMENTATION.md`, `README.md`, `docs/phase-2-operations.md`, contract tests.

- [x] Update generated and checked-in contracts for error statuses, deprecated ignored ownership inputs, media bounds, visibility/history policy and secure download headers.
- [x] Document rollout settings, canonical storage ownership, retained files, replacement cleanup, compatibility tightening and no migration requirement.
- [x] Run `./mvnw --batch-mode verify -Dlogging.level.root=WARN`, OpenAPI lint and `git diff --check`.
- [x] Review all changed authorization/storage/config paths against roadmap and record evidence and limitations here.

## Execution ledger

- Baseline: `./mvnw --batch-mode verify -Dlogging.level.root=WARN` passed: 62 tests, zero failures/errors/skips; executable JAR built. Existing Mockito/JVM, Flyway version, JPA and springdoc warnings observed. Migration rejection error logs belong to passing invalid-legacy-data tests.
- Pre-flight: Tasks 1/2 share the file parent-post boundary; policy runs before storage access and before locked mutations. Tasks 2/3 share 413 handling; the validator and servlet multipart limit use the existing JSON envelope. Task 4 consumes all controller/DTO changes; checked-in OpenAPI must be regenerated after annotations settle.
- Ruling: Execute inline on the requested dedicated branch and keep this tracked plan as the ledger — user delegated remaining choices, and commits were not requested — changes remain uncommitted for review.
- Task 1: complete. New authorization matrix first failed on 10 private-history/vault/concealment cases; policy changes passed all 58 focused authorization/deletion/service tests, including MySQL races and rollback.
- Task 2: byte/pixel/signature/symlink/header/avatar-rollback regressions first failed, then passed. Avatar rows use a pessimistic lock and post-commit old-file cleanup; attachment rollback retains no newly written objects.
- Ruling: Use TwelveMonkeys ImageIO 3.15.0 for WebP decoding — JDK ImageIO cannot decode the existing allowed avatar format — adds one dependency family rather than accepting signature-only WebP uploads.
- Task 3: unsafe signing-key/CORS and JSON validation regressions first failed (16 cases), then passed. Previously issued JWT compatibility passed. Missing/private file responses and duplicate-vault conflict regressions also observed RED→GREEN. Latest focused run: 67 tests, zero failures/errors/skips.
- Task 2: complete. Configured-root symlink read-after-write regression observed RED→GREEN; actual WebP avatar bytes decode successfully through shared validation. MySQL integration verifies avatar commit/rollback and attachment transaction rollback with spoofed ownership input.
- Task 3: complete. Security/validation behavior and existing JWT flows pass the full suite.
- Task 4 verification before independent review: `./mvnw --batch-mode verify -Dlogging.level.root=WARN -DtrimStackTrace=true` passed, 118 tests, zero failures/errors/skips, executable JAR built. Generated/checked-in affected operations and request schemas match. `npx --yes @redocly/cli@2.78.0 lint --extends=minimal --format=summary openapi.yaml` passed with the same 135 broader documentation warnings as Phase 1. `git diff --check` passed.
- Packaged-artifact smoke: started the executable JAR under strict `prod` configuration against disposable MySQL 8.4, registered/logged in with a real JWT, uploaded/read a valid WebP avatar, verified accepted/rejected HTTP preflights, and verified a real 11 MiB multipart request returns the JSON 413 envelope. Java process, temporary storage and container were removed in finally blocks; no existing database was used.
- Runtime investigation: Python urllib initially reported BrokenPipe while still sending rejected upload bytes. A raw HTTP client independently read the server response after the write interruption and verified JSON 413. Tomcat's documented bounded aborted-upload swallowing explains the transport behavior; no application limit or unbounded-drain workaround was introduced.
- Additional validator boundary coverage verifies empty/unsupported uploads, MIME mismatch, PDF rejection for avatars and oversized streams with dishonest reported sizes. Final suite after these additions: `./mvnw --batch-mode verify -Dlogging.level.root=WARN -DtrimStackTrace=true` passed, 121 tests, zero failures/errors/skips, executable JAR built.
- Independent whole-branch review found two Important issues: stale profile writes can overwrite a replaced avatar reference; generated/checked-in request schemas agree on camelCase names that runtime rejects. Both reproduced with real MySQL/MVC regressions: stale profile commit restored `old.png`, and a request constructed using generated vote field names returned 400.
- Final: Ruling: use Hibernate dynamic updates for user scalar writes — changing only dirty columns prevents unrelated profile/role/password updates from overwriting the avatar, while existing avatar row locks serialize replacements — same-field concurrent updates retain existing last-writer semantics and dynamic SQL may reduce statement reuse.
- Final: Ruling: regrade the whole-batch fixture finding as Important test coverage — atomic rejection of a batch containing a later invalid file is a Phase 2 acceptance requirement, and the signature-only first fixture did not test it — correcting the fixture costs no production change.
- Final: Ruling: retain trusted principal-derived controller actor arguments, exclude scanning/purge/token migration, and keep documented crash-orphan/hostile-filesystem limitations — these are existing API contracts and explicit scope boundaries — later direct service consumers and storage reconciliation must honor those assumptions.
- Final: Ruling: active APPROVED historical rows remain readable while revisions await moderation; SUPERSEDED rows remain owner/admin-only — preserves recorded versioning policy and current successful clients — broader historical privacy requires a separate behavior change.
- Final fix pass: user dynamic updates made `staleProfileEditCannotRestoreDeletedAvatarReference` RED→GREEN without changing schema or serializing unrelated profile edits. Explicit JSON field mappings made `documentedRequestPropertyNamesWorkThroughMvc` RED→GREEN for vote, vault add/label edit and post creation; affected schemas now document actual snake_case fields. Batch validation fixture now starts with a genuinely decodable PNG. Focused regressions and contract parity pass: 50 tests, zero failures/errors/skips. All review findings addressed; none deferred.
- Final: fixed both Important findings — the two reproducing integration tests observed RED→GREEN; corrected batch fixture also passes. Final full-suite command `./mvnw --batch-mode verify -Dlogging.level.root=WARN -DtrimStackTrace=true` → BUILD SUCCESS, 123 tests, zero failures/errors/skips, executable JAR built. Pinned Redocly lint passed with 135 existing warnings; `git diff --check` passed. Task 4 complete. Branch retained uncommitted for user integration; no push or deployment performed.
