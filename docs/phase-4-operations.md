# Phase 4 — Password recovery

## API and client flow

Recovery defaults **disabled** until its provider and secrets are configured. Valid calls to the two new routes return JSON 503 while disabled; existing authentication remains available. The only anonymous authentication routes are the six explicit POST register/login/refresh/logout/recovery/reset operations.

1. POST `/api/v1/auth/password-recovery` with `{"email":"person@example.test"}`. Eligible, missing, deleted and banned accounts receive the same **202** message: `If the account is eligible, password reset instructions will be sent.` No response discloses a token or account status. Addresses are trimmed/lowercased and use the existing ASCII account format, maximum 254 characters; Unicode collation aliases cannot bypass the per-email budget.
2. Eligible users receive a plain-text email linking to the configured frontend URL followed by `#token=<43-character-base64url-token>`. A fragment avoids sending the token in frontend HTTP request URLs. The backend never builds links from Host/Forwarded headers or fetches the submitted URL. Frontend code must read the fragment, keep the token out of analytics/logs/persistent storage, and submit it in the next JSON request. Frontend page wiring remains separate work.
3. POST `/api/v1/auth/password-reset` with `{"token":"<email token>","new_password":"<new password>"}`. Passwords require at least 8 characters, uppercase/lowercase/digit, and at most **72 UTF-8 bytes** (BCrypt limit). Success returns **200** `Password reset successfully. Sign in again.` It does not return new credentials or create a session.

Invalid/expired/superseded/used/ineligible tokens cannot change a password. Token-state failures use the same generic JSON 400; structurally malformed fields/password-policy failures also return 400 validation envelopes. New requests supersede prior unused tokens. Default token lifetime is 15 minutes, configurable from 1 minute to 1 hour.

A successful reset updates BCrypt password, marks token used, invalidates other unused reset tokens, and deletes **all stored refresh sessions** in one transaction. Existing short-lived access JWTs keep their remaining normal lifetime; this phase adds no token-version claim, signing-key rotation or forced access-token migration. A fresh login requires the new password. Legacy register/login/refresh/logout success shapes remain supported, including opaque stored refresh tokens and snake_case `refresh_token` input. Login/refresh issuance shares an account-row lock with reset; refresh rechecks its session using a current locking read so an earlier database snapshot cannot resurrect a revoked session.

## Deployment configuration

Apply V5 after Phase 3. It adds `password_reset_tokens` and `password_recovery_rate_limits`; it does not alter existing users/passwords/sessions or earlier migration checksums. Existing refresh sessions are revoked only when that user's reset succeeds, not by migration/startup.

Set the following through environment/deployment secrets (or corresponding `app.password-recovery.*` and Spring Mail properties):

```dotenv
PASSWORD_RECOVERY_ENABLED=true
PASSWORD_RECOVERY_URL=https://your-frontend.example/reset-password
PASSWORD_RECOVERY_FROM=arca@your-domain.example
PASSWORD_RECOVERY_RATE_KEY_SECRET=<separate random Base64 secret of at least 32 bytes>
SPRING_MAIL_HOST=<SMTP provider host>
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<SMTP login>
SPRING_MAIL_PASSWORD=<SMTP credential>
```

Generate the rate-key secret separately with `openssl rand -base64 32`; do not reuse the JWT key. All application instances sharing a database must share this rate key and sensible synchronized UTC clocks. Rotating it resets hashed rate identities for future requests but does not invalidate reset-token hashes or access JWTs. Never commit populated `.env` files or provider credentials.

Production links require HTTPS, a host and no userinfo/query/fragment. The sender address is validated. Default SMTP uses required STARTTLS, hostname verification, authentication and finite 5-second connection/read/write timeouts. SMTPS is supported with its own finite timeout properties and hostname verification. Production SMTP requires credentials and TLS; protocol debugging is rejected. Development/test profiles can use a local unauthenticated plaintext SMTP sink explicitly. Enabling with missing/unsafe settings or no ready provider fails startup; disabled installations need no SMTP host/provider. Readiness validates settings but does not attempt a network connection, so provider outages do not prevent application startup.

Optional settings:

| Environment variable | Default / bound |
| --- | --- |
| `PASSWORD_RECOVERY_TOKEN_TTL` | `PT15M`, minimum `PT1M`, maximum `PT1H` |
| `PASSWORD_RECOVERY_RATE_WINDOW` | `PT15M`, minimum `PT1M`, maximum `PT24H` |
| `PASSWORD_RECOVERY_EMAIL_LIMIT` | 3 recovery requests per normalized email/window |
| `PASSWORD_RECOVERY_IP_LIMIT` | 10 recovery requests per client IP/window |
| `PASSWORD_RECOVERY_RESET_IP_LIMIT` | 20 reset attempts per client IP/window |
| `PASSWORD_RECOVERY_TRUSTED_PROXIES` | Empty; explicit comma-separated proxy IP/CIDR allowlist only |

Keep `server.forward-headers-strategy=none` (configured explicitly); enabling recovery rejects native/framework rewriting that would conceal the socket peer. Do not install another filter/valve that rewrites the peer before the recovery resolver. When trusted proxies are empty, all forwarded headers are ignored and the socket peer is the rate identity. Behind a proxy this intentionally groups clients until the operator configures its precise trusted IP/CIDRs. Never trust `/0` or arbitrary public client ranges. For a trusted peer, parse a bounded X-Forwarded-For chain right-to-left and stop at the first untrusted hop; ignore spoofed leftmost entries. Accept numeric IPv4/IPv6 literals, not hostnames, ports, shorthand or DNS lookups. Proxy routing/header sanitization must be rehearsed in the actual deployment before enabling.

## Rate controls and concurrency

Both normalized-email and client-IP recovery budgets apply regardless of account eligibility. Reset attempts have a separate client-IP budget. **429** uses the standard JSON error envelope and `Retry-After` seconds. IP budget is acquired first; once exhausted, requests cannot allocate unbounded arbitrary email buckets. Database row locks enforce the same windows across instances and concurrent requests; HMAC/domain-prefixed keys retain neither plaintext email nor IP. Budget transactions commit independently of invalid reset attempts and later account rollbacks.

`Retry-After` is CORS-exposed to allowed frontend origins so browser clients can implement backoff. CIDR prefixes are checked numerically; equivalent all-address masks such as `/00` and `/000` are rejected along with `/0`.

HTTP orchestration completes rate transactions before starting account work, avoiding connection-pool starvation from nested independent transactions in ordinary requests. Account work locks the user before any mutable reset/session row. Token issue/consume, validated login/session creation and refresh validation follow this order. Simultaneous consumes succeed once; concurrent requests leave one usable token. Tests reproduce both validated-login/reset and old-refresh-snapshot/reset races.

Email-provider work runs only after token transaction commit, on a dedicated two-thread executor with a 100-task queue. SMTP latency and exceptions do not affect the public account-eligibility response. Every allowed recovery request performs random/digest work; account lookup/persistence is synchronous, so this is not a strict constant-time guarantee under database contention. Rate limiting and generic wording do not constitute a general-purpose DDoS defense for other auth routes.

The queue is **process-local, not a durable outbox**. A provider failure, queue saturation, process crash or shutdown may lose delivery; callers still receive generic 202 and may retry within budgets. A retry supersedes older unused links, so delivery order need not equal request order. Do not persist raw tokens/links to make the queue durable; durable encrypted delivery would need a separate design. Provider exceptions and stack traces can contain URLs/credentials, so delivery logs use fixed content-free outcomes only.

## Retention, rollout and observations

Only SHA-256 digests of 32 SecureRandom token bytes are stored. New timestamps are UTC DATETIME(6), without changing Phase 3's legacy post wall-clock interpretation. Used/revoked rows remain until their expiry is at least 30 days old. Hashed rate rows are removed after the window has been expired at least one day. An hourly enabled-only job, delayed one hour after startup, deletes in bounded 10,000-row batches (at most ten batches/run); operators must watch growth/backlog at larger volumes. User hard deletion cascades reset tokens; existing user soft deletion leaves retained rows unusable. No reset tokens or rate rows are exposed through public APIs.

Back up and rehearse V4→V5 with representative users/sessions; verify table/index/FK creation and retained old sessions/passwords. Deploy disabled, configure secrets/provider/frontend/proxy policy, validate readiness and SMTP delivery against a controlled mailbox, then enable clients. Do not send real messages from automated verification. If disabling during an incident, valid reset calls return 503; existing unused tokens remain stored until expiry/cleanup and are usable again only if reenabled before expiry.

New routes return safe/generated `X-Request-ID` and emit content-free `password_recovery` / `password_reset` outcomes. Meters `arca.auth.password.recovery`, `arca.auth.password.reset` and `arca.auth.password.delivery` (success/failed/queue_full) contain no email, IP, token, password or URL tags. Existing post search/deletion metric names remain stable. Actuator stays admin-only, with process-local meters by default. Watch rejection/delivery-failure/queue-full rates, SMTP timeouts, row cleanup backlog and account-lock latency. No Phase 9 audit store is introduced.

Run full Maven verification and pinned OpenAPI lint. Coverage includes generic eligibility, malformed/expired/reused/superseded/concurrent tokens, BCrypt UTF-8 bounds, session/access-JWT compatibility, normalized/distributed rate windows, proxy spoofing, queue failure/saturation/nonblocking delivery, rollback suppression, cleanup, configuration rejection and clean/legacy/V4→V5 migration preservation.
