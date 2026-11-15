# Phase 4 Password Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let eligible email owners reset passwords once using expiring email tokens, with session revocation and no account enumeration.

**Architecture:** Keep Spring MVC/services/repositories. A JDBC reset-token repository shares account transactions with JPA; account-row locking serializes token issue/consume and login/refresh. Independent database-backed rate windows survive rejected/reset transactions. A bounded post-commit mail executor sends through an interface and configurable SMTP adapter.

**Tech Stack:** Java 21, Spring Boot 3.5.5, Spring Security BCrypt/JWT, Spring Mail, MySQL 8.4/Flyway, JUnit/Testcontainers.

**Spec:** `docs/arca-backend-roadmap.md` Phase 4 and cross-cutting requirements; user delegated successive roadmap delivery.

## Global Constraints

- Branch `feat/auth-password-recovery` starts from `develop` merge `7f2717e`. No commit/push/deployment or use of existing databases without a separate request.
- Preserve register/login/refresh/logout routes, successful response shapes, BCrypt hashes, role names, JWT claims and normal remaining lifetime of issued access JWTs.
- Add anonymous POST `/api/v1/auth/password-recovery` (email, generic 202) and `/api/v1/auth/password-reset` (token/new_password, generic success 200 requiring fresh login). No token/email/account-status response fields.
- Tokens: 32 SecureRandom bytes, Base64url without padding, SHA-256 hex digest only in database, default TTL 15 minutes (bounded configuration). Store UTC creation/expiry/used/revoked timestamps. Invalidate previous unused tokens under the same account lock.
- Completion checks eligibility/expiry/unused/unrevoked after acquiring the account lock, BCrypt-hashes new password, consumes token and deletes all refresh sessions in one transaction. Invalid, expired, superseded, reused or ineligible tokens return the same generic 400.
- Password policy: existing minimum 8 characters with uppercase/lowercase/digit, plus explicit BCrypt maximum 72 UTF-8 bytes; do not change existing successful registration contracts.
- V5 adds reset-token and recovery-rate tables only; retain V1–V4 unchanged. User hard deletion cascades reset tokens; soft deletion makes recovery ineligible.
- Recovery requests for existing/nonexistent/deleted/banned accounts share wording/status, token-generation work, rate checks and nonblocking mail dispatch behavior. SMTP errors/queue saturation must not change public eligibility response or log secrets.
- Provider-neutral SMTP adapter, dedicated bounded executor (2 threads, queue 100), TLS/timeouts required for production. Reset link uses configured frontend URL and `#token=...` fragment so raw token is not sent in frontend HTTP request URLs. No Host/header-derived reset URL.
- Feature defaults disabled to preserve bootability without mail credentials; disabled new routes return JSON 503. Enabling requires validated reset URL, sender, separate Base64 rate-key secret (>=32 bytes), and a ready email provider. Tests use an in-memory mail implementation.
- Rate windows default 15 minutes: recovery per normalized email 3, per trusted client IP 10, reset per IP 20; 429 includes Retry-After. Keys are domain-separated HMAC digests, not raw email/IP. Default uses direct socket peer and ignores forwarded headers; explicit trusted proxy CIDRs enable bounded right-to-left X-Forwarded-For interpretation.
- Delivery queue is process-local, not a durable outbox: a crash/failure may require retry, which supersedes prior tokens. Document this limitation rather than persisting raw tokens for delivery.

## Review Focus

- Concurrent reset/issue/login/refresh must use account-first lock order and current token reads, so stale credentials/sessions cannot survive reset.
- Unknown/deleted/banned accounts and SMTP failures must not differ in public response or disclose tokens through errors/logs.
- Rate enforcement must work across instances and rollbacks, reject spoofed forwarded IPs, bound key growth and emit useful Retry-After.
- Token generation, link construction, UTC expiry boundaries and BCrypt byte limits must remain correct for malformed/Unicode inputs.
- Existing auth contracts, JWT acceptance and other phases' migrations/tests must remain compatible; no production mail is sent during verification.

---

### Task 1: Endpoint/migration and token lifecycle regressions

**Files:** new recovery endpoint/integration tests, `V5__password_recovery.sql`, recovery DTOs/service/repository/config.

**Interfaces:** `PasswordRecoveryService.request(String email, String clientIp)`, `reset(String token, String newPassword, String clientIp)`; immutable request DTOs use explicit snake_case wire fields. `RecoveryEmailSender.sendResetLink(String email, URI link)` and `validateConfiguration()` isolate provider/test implementations.

- [x] Add failing endpoint availability, generic eligibility response and migration expectations; observe RED.
- [x] Add JDBC hash-only token storage, issue/supersede/expiry/consume semantics and account-lock transaction boundaries, with Clock injection for tests.
- [x] Test existing/nonexistent/deleted/banned, valid/expired/reused/superseded/malformed/random tokens, password policy, transaction rollback and hash/log secrecy.

### Task 2: Session serialization and persistent abuse controls

**Files:** `AuthSessionService`, existing auth controller/refresh/user repositories, recovery limiter/client-IP resolver and focused tests.

**Interfaces:** locked login/refresh return existing `AuthResponse`; refresh revalidates stored session with a current locking read after account lock. Limiter commits independent IP/email windows and returns retry duration; resolver uses only configured trusted peers.

- [x] Reproduce concurrent reset/reset, issue/issue, login/reset and refresh/reset cases on MySQL.
- [x] Implement account-first locks while preserving existing auth HTTP success/error compatibility and opaque stored refresh tokens.
- [x] Test email normalization, per-email/IP limits, shared database persistence, expiry/rollback behavior, forwarded-header spoofing and concurrent requests.

### Task 3: Delivery, startup configuration and operations

**Files:** recovery configuration/properties, SMTP adapter/dispatcher, cleanup and observation filters; `.env.example`/runbook notes.

- [x] Test bounded asynchronous post-commit dispatch, SMTP failure/queue behavior and rollback suppression without returning or logging raw tokens.
- [x] Validate enabled settings, provider readiness, frontend URL/TLS and mail timeouts; keep disabled installations bootable.
- [x] Add content-free request correlation/outcome metrics and bounded expiry/retention cleanup; test cleanup preserves valid records.

### Task 4: Contracts, migration rollout and final review

**Files:** OpenAPI/controller/DTO annotations, README/API notes, `docs/phase-4-operations.md`, contract/migration tests.

- [x] Document real request/response/error/header schemas and generic recovery behavior, rate/proxy policy, SMTP configuration, session/access-token semantics and retention.
- [x] Verify clean/baseline/V4→V5 migrations without modifying earlier migration checksums.
- [x] Run full Maven verify, pinned OpenAPI lint, diff checks and disposable SMTP/JAR smoke; obtain independent review, fix required findings and record evidence.

## Execution ledger

- Working tree clean; develop contains merged Phase 3 (`7f2717e`). Created requested next-phase feature branch.
- Ruling: execute inline with SMTP as the provider-neutral adapter and conditional feature enablement — no existing mail infrastructure is configured in repository — existing deployments remain bootable, while operators must configure/enable recovery before clients use it.
- Pre-flight: reset issuance/completion and session creation/refresh must share account-row locking. Rate transactions must commit before account work to avoid account/rate lock cycles. Post-commit delivery must never persist raw tokens or block the generic HTTP response on SMTP.
- Initial RED: missing new routes returned 500 rather than disabled 503/generic 202; clean Flyway ran four migrations instead of five. Fixture correction supplied the existing required banned-email creation timestamp before interpreting lifecycle regressions.
- Core lifecycle GREEN: generic eligibility, hash-only storage, UTC TTL, superseding, one-time consumption, BCrypt persistence, invalid-token/password rejection, revocation, rollback/mail suppression and independent abuse budgets passed. Two session races first observed RED: refresh from a pre-reset snapshot minted access, and validated login did not hold the account lock. Locked session service/current token recheck turned both GREEN.
- Ruling: narrow legacy auth anonymous permission to six explicit POST routes — new recovery access must not accidentally expose future auth-prefixed routes — successful old routes remain unchanged; unsupported methods now require authentication.
- Ruling: separate rate work from account transactions and keep rate writes independently committed — invalid reset rollbacks must not refund abuse budgets and nested ordinary account/rate transactions can starve a pool — budget consumption may remain even if later account work rolls back.
- Configuration/delivery controls RED→GREEN: missing observations/retention job, trusted IP shorthand acceptance and SMTPS checking the wrong timeout namespace reproduced, then fixed. Production TLS hostname verification and native/framework forwarded-header bypass also observed RED→GREEN. Actual literal-IP parsing performs no hostname DNS lookups.
- Ruling: retain the existing ASCII account-email format in recovery — MySQL's accent-insensitive email collation otherwise lets Unicode aliases reach the same account with different email-rate keys — internationalized account addresses need a separate identity/collation design. Alias regression observed 202 before validation, now 400.
- Provider tests cover blocked SMTP without blocked HTTP, failure-exception redaction and queue saturation at two workers/100 queued tasks. Concurrent budget tests allow exactly 3 of 8 same-email attempts; simultaneous issue leaves one usable token and simultaneous consume succeeds once.
- Contract drift RED→GREEN: new request/response operations and schemas added, token/new_password marked write-only, and legacy refresh-token schema corrected to its existing snake_case wire field. Startup configuration tests pass for defaults, explicit custom provider and TLS SMTP/SMTPS; invalid provider, key, URL, lifetimes, limits, timeouts and forwarded strategy fail closed.
- Full verification before review: `./mvnw --batch-mode verify -Dlogging.level.root=WARN -DtrimStackTrace=true` → BUILD SUCCESS, 200 tests, zero failures/errors/skips, executable JAR built. This includes clean/baseline/V4→V5 data/session preservation, digest uniqueness and user-FK cascade. Explicit anonymous OpenAPI security declaration first failed its contract assertion, then passed after adding empty security requirements; pinned Redocly lint returned to 135 existing warnings. `git diff --check` passed.
- Independent review found no Critical/Important defects and one browser-facing Retry-After exposure gap. Final: Ruling: treat usable cross-origin retry metadata as required API behavior — frontend clients cannot honor the advertised delay otherwise — expose the existing nonsecret header with an allowed-origin 429 regression; no payload/status change.
- Final pass also reproduced unsafe equivalent zero-mask CIDRs `/00` and `/000` passing configuration validation. Numeric prefix bounds now reject every zero-mask spelling rather than checking only the literal `/0` suffix. Both cases first observed RED.
- Packaged-JAR/MariaDB-driver smoke passed using environment-based enablement and a real loopback SMTP sink: fragment link delivery, exact SHA-256 digest persistence, reset/login/password change, all-refresh revocation, old-access JWT acceptance, one-use rejection, rate headers and log secrecy. Java process, SMTP server, temporary storage and disposable database removed; no real mail or existing database used.
- Final: Ruling: frontend fragment extraction stays separate, and delivery remains a bounded process-local queue — these are the accepted backend scope/provider choices — clients must wire the new fragment contract, and crashes/provider drops can require another request. No raw-token outbox is introduced.
- Final fix verification: allowed-origin 429 Retry-After exposure and both zero-prefix spellings observed RED→GREEN. No review findings deferred. Final `./mvnw --batch-mode verify -Dlogging.level.root=WARN -DtrimStackTrace=true` → BUILD SUCCESS, 202 tests, zero failures/errors/skips, executable JAR built. Pinned Redocly 2.78.0 minimal lint passed with 135 existing warnings; `git diff --check` passed. All tasks complete; changes remain uncommitted on Phase 4 branch pending user instruction. No push, real mail delivery or existing-database migration performed.
