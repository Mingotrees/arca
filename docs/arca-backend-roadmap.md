# Arca Backend Roadmap

**Planning baseline:** 2026-10-16\
**Target stack:** existing Spring Boot 3 / Spring MVC / Spring Data JPA / MySQL / Spring Security JWT application\
**Roadmap type:** additive evolution of the existing `/api/v1` backend

## 1. Purpose and guardrails

This roadmap closes the backend gaps required by the current Arca product direction without replacing the backend or forcing a coordinated frontend rewrite. Work should proceed as small, independently releasable changes in the order below.

### Non-negotiable constraints

- **Additive changes only.** Prefer new nullable columns, new tables, new indexes, optional response fields, and new endpoints. Do not destructively rename or remove existing database columns, routes, request fields, response fields, status values, or roles during this roadmap.
- **No backend rewrite.** Keep the existing controller → service → repository structure and Spring MVC/JPA implementation. Introduce shared policies or query abstractions only where they remove a concrete correctness or security risk.
- **Preserve authentication compatibility.** Existing JWT access tokens, refresh-token behavior, `ROLE_USER` / `ROLE_ADMIN` authorization, and `Authorization: Bearer` clients must continue to work. Any stronger token invalidation mechanism must accept tokens issued before deployment for their normal remaining lifetime or be released as an explicitly managed authentication migration.
- **Preserve API compatibility.** Existing `/api/v1` endpoints and their successful response shapes remain supported. Add optional fields freely; introduce a new endpoint when pagination or a different envelope would otherwise change a legacy `List<T>` response into an incompatible object.
- **Preserve serialization conventions.** Continue the configured `snake_case` JSON contract and the current row-ID/logical-post-ID distinction until a separately versioned contract can replace it. Document that distinction wherever IDs are accepted.
- **Migrations must be safe to roll forward.** Schema evolution must not depend on Hibernate performing destructive DDL at startup. Large backfills and index creation require a deployment plan appropriate to the production MySQL version and data volume.
- **Authorization is enforced server-side.** Client visibility, submitted `user_id` values, and hidden buttons are never authorization controls. Identity comes from the authenticated principal.

### Delivery rule

A phase is complete only when its schema migration, implementation, authorization behavior, OpenAPI contract, focused automated tests, and operational notes are merged together. Later phases may rely on earlier phases, but must not be pulled forward in ways that bypass this sequence.

## 2. Current backend capabilities

The current repository already provides a usable base:

- **Authentication and sessions:** registration, login, access JWTs, persisted refresh tokens, refresh, logout, BCrypt password hashing, stateless Spring Security, and role-based access control.
- **Users and profiles:** user retrieval and search, current-user profile update, administrative user operations, user soft deletion, multiple roles, profile-picture upload/read, and banned-email administration.
- **Academic taxonomy:** school, department, and subject entities with administrative CRUD and relationships between departments and subjects.
- **Posts:** create, retrieve, update by creating a new version, logical post/version history, latest-version selection, approval/rejection workflow, owner/admin modification checks, and listings by user, subject, department, status, or moderation queue.
- **Post interaction:** file attachments/downloads, upvotes/downvotes, the current user’s vote, and per-user vault/bookmark entries with labels.
- **API and operations:** springdoc OpenAPI support, JSON security errors, configurable CORS, Docker deployment assets, validation, global exception handling, and focused controller/service/security tests.

The roadmap should extend these capabilities rather than duplicate them under a parallel architecture.

## 3. Confirmed backend gaps

The following gaps are present in the current backend and are not merely frontend assumptions:

1. `DELETE /api/v1/posts/{rowId}` validates owner/admin access but deliberately returns **501 Not Implemented**; posts have no deletion columns or deletion filtering.
2. Resource authorization exists in selected paths, but there is no single visibility policy consistently protecting a post and its related files, votes, and vault operations. File and media handling also need explicit type, size, path, and response-header hardening.
3. Post lists are unpaged. There is no general post text search, combined filtering, supported sort contract, or stable server-side pagination.
4. Authentication has no forgot-password, reset-token, email-delivery, or password-reset flow.
5. Posts have timestamps, but users and subjects do not. There are no user/subject statistics endpoints or aggregate queries for profile metrics.
6. Profiles support an avatar only; there is no cover-image field, storage policy, or API.
7. Posts have subjects and files but no structured link model or request/response contract.
8. Admin APIs manage accounts and banned email addresses but expose no dashboard aggregate endpoint.
9. There is no durable audit-event model or admin audit query API.
10. There is no announcements model/API and no backend trending calculation. Current UI examples are static.
11. No server-side draft entity or draft status exists.
12. No explicit database migration tool or migration directory is present; schema management is effectively outside a versioned migration history.

## 4. Draft storage decision

### Decision: drafts remain frontend-only in IndexedDB for now

“Save Draft” will persist a user’s in-progress post in browser **IndexedDB**. This roadmap does **not** add a backend draft status, draft table, draft endpoint, or draft synchronization. A draft must not call post creation, upload permanent attachments, enter moderation, or affect backend counts.

Recommended client-side record boundaries are: authenticated user ID, local draft UUID, title/content, selected department and subjects, structured-link input, attachment metadata or browser blobs where quota permits, `created_at`, `updated_at`, and a local schema version. Draft keys must be scoped by user so one account cannot see another account’s local drafts on a shared browser.

### Accepted limitations

- Drafts are tied to a browser profile and origin; they do not synchronize across devices or browsers.
- Clearing site data, private-browsing expiry, browser eviction, or storage corruption can remove them. IndexedDB is convenience persistence, not a backup.
- Quotas differ by browser. Large attachment blobs may fail to save and must produce a visible client-side warning rather than a partially saved draft.
- Drafts are unavailable to admins, moderation, support, search, analytics, and account recovery.
- There is no collaboration, merge, conflict resolution, server revision history, or guaranteed offline-to-online synchronization.
- Because local drafts may contain sensitive content, the frontend must avoid logging their body, scope them to the signed-in user, and define logout/shared-device behavior.
- A local draft becomes a backend post only after an explicit submit succeeds. The frontend should retain the local draft on a failed submission and remove or mark it submitted only after receiving the create response.

### Deferred server-draft trigger

Reconsider server drafts only when cross-device continuity, collaboration, support recovery, durable attachment staging, or product analytics becomes a committed requirement. That work should receive its own threat model and API design rather than overloading `PENDING_APPROVAL`.

## 5. Phased roadmap

### Phase 1 — Post soft deletion

**Scope**

- Implement the existing post delete route for a post owner or admin.
- Treat deletion as a **logical-post operation**: resolving any version row identifies its logical `post_id`, and every version of that logical post becomes deleted atomically. This prevents an older approved version from resurfacing.
- Exclude deleted posts from public lists, direct reads, moderation queues, history, search inputs, votes, vault additions, and attachment access.
- Retain associated rows and stored files initially. Physical purge and restore UI are separate retention decisions.

**Likely API and schema changes**

- Add `is_deleted BOOLEAN NOT NULL DEFAULT FALSE`, `deleted_at DATETIME NULL`, and `deleted_by BIGINT NULL` to `posts`; add an index that supports active/latest/status queries. Use a non-cascading actor reference or nullable foreign key so later user deletion does not erase provenance.
- Change `DELETE /api/v1/posts/{rowId}` from 501 to `204 No Content` on success. Keep the path and `rowId` meaning unchanged.
- Repeating the delete may return `204` for idempotency, while callers without visibility should receive the project’s chosen `404`/`403` policy consistently.
- Do not add delete fields to normal `PostResponse`. A future admin-only restore endpoint is not required in this phase.

**Compatibility notes**

- Existing reads and writes keep their current shapes; only previously visible deleted records disappear.
- Existing row IDs, logical post IDs, versions, votes, vault rows, and attachment records remain stable.
- Update every explicit JPQL/native query; do not rely solely on a Hibernate annotation that can be bypassed by custom queries.

**Focused tests**

- Owner, admin, another user, unauthenticated user, missing row, and repeated-delete controller/security cases.
- Repository tests proving all versions are marked in one transaction and no active query returns any deleted version.
- Tests for direct read, history, pending queue, file download, voting, and vault access after deletion.
- Migration test against representative existing post/version data.

**Acceptance criteria**

- Authorized delete returns 204 and no version of the logical post is accessible through standard APIs.
- Unauthorized deletion cannot change data.
- No older version becomes “latest” or visible after deletion.
- No attachment is physically removed by the request, and the retention behavior is documented.
- OpenAPI no longer advertises 501 for this operation.

### Phase 2 — Resource and security hardening

**Scope**

- Define one service-level post visibility/mutation policy and use it for posts, files, votes, and vault operations.
- Derive the acting user from the JWT principal; ignore legacy request `user_id` for ownership and mark it deprecated in OpenAPI.
- Apply explicit upload controls: allowlisted media/document types, maximum request/file sizes, extension/content checks, generated server filenames, canonical storage paths, and safe download headers.
- Fail closed on missing JWT secrets or unsafe production CORS settings; preserve configured development origins.
- Normalize authorization and validation errors without leaking whether inaccessible private/pending/deleted resources exist.

**Likely API and schema changes**

- Primarily service/security configuration changes; no schema change is expected.
- Existing endpoints may now return correct `400`, `401`, `403`/`404`, `413`, or `415` responses for requests that were previously accepted or failed generically.
- Document media size/type limits and authorization responses in OpenAPI. Preserve existing successful payloads.
- If request throttling is introduced, expose standard `429 Too Many Requests` and `Retry-After`; implementation must work correctly behind the deployed proxy before enablement.

**Compatibility notes**

- This is intentionally behavior-tightening, not a route redesign. Legitimate owner/admin and approved-post flows must remain unchanged.
- Public profile-picture reads may remain public for compatibility, but upload stays owner/admin only. Any move to private media requires a separate product decision.
- Do not globally make all post APIs public while solving media access; current JWT expectations remain in force.

**Focused tests**

- A resource-action matrix covering anonymous, owner, another user, admin, approved, pending, rejected, and deleted states.
- IDOR tests for guessed post, file, vault, and user IDs.
- Multipart tests for oversize, spoofed type/extension, traversal filename, empty file, unsupported media, and safe `Content-Disposition`.
- Configuration tests for missing/weak JWT secret, CORS origins, and consistent JSON errors.

**Acceptance criteria**

- Every related-resource operation invokes the same visibility decision for the parent post.
- No client-supplied user ID can transfer ownership or act as another user.
- Disallowed uploads never create database rows or files; failed transactions clean up partial files.
- Security regression tests demonstrate no cross-user read or write path for private/pending/deleted resources.
- Existing successful API contracts and JWT flows continue to pass.

### Phase 3 — Post search, filter, sort, and pagination

**Scope**

- Add one composable post query supporting text search and filters instead of multiplying list endpoints.
- Initial filters: `q`, `subject_id`, `department_id`, `author_id`, and optional date range. Public/user results contain only approved, latest, non-deleted posts; moderation status filtering remains admin-only.
- Supported sorts: newest, oldest, relevance when `q` is present, and vote score. All sorts include a deterministic row-ID tie-breaker.
- Use zero-based pages, default size 20, and maximum size 100. Reject unsupported sort names rather than passing arbitrary property names into JPA.

**Likely API and schema changes**

- Add `GET /api/v1/posts/search` returning a new page envelope such as `content`, `page`, `size`, `total_elements`, `total_pages`, and `sort`.
- Keep `GET /api/v1/posts` and current subject/department/user routes returning `List<PostResponse>` unchanged. They may delegate internally to the new query while preserving semantics.
- Add composite indexes for active/latest/status and common department/user/subject paths. Add a MySQL full-text index on post title/content if relevance search is selected; record the chosen tokenizer/collation in the migration.
- Reuse `PostResponse`; new summary DTOs are acceptable only if their contract is explicit and avoids silently removing existing fields.

**Compatibility notes**

- A new route avoids changing a legacy list response into a page object.
- “Trending” is not a sort alias in this phase; it remains Phase 10. Vote score is a deterministic aggregate sort, not a time-decayed trend.
- Existing consumers can migrate incrementally and need not adopt pagination immediately.

**Focused tests**

- Each filter alone and in combination; blank/escaped/non-ASCII query; boundary dates; invalid sizes/sorts; empty and last pages.
- Stable ordering across equal timestamps/scores and no duplicates from subject joins.
- Visibility tests ensuring pending, rejected, historical, and deleted versions never leak to regular users.
- Repository integration tests against MySQL-compatible search behavior, plus query-plan checks on production-like data volume.

**Acceptance criteria**

- Search returns the same record set across repeated calls when data is unchanged, with stable paging and no duplicate rows.
- Combined filters use intersection semantics and totals match the filtered result.
- Page-size limits and sort allowlists are enforced.
- Legacy list endpoints remain contract-compatible.
- Relevant indexes and an observed query plan are captured in the PR notes.

### Phase 4 — Password recovery

**Scope**

- Add request and completion flows for users who control the registered email address.
- Always return a generic request response to prevent account enumeration.
- Generate a high-entropy, single-use token; store only its cryptographic hash; expire it; invalidate previous unused tokens for that user; and never log the raw token.
- On successful reset, BCrypt-hash the new password, mark the token used, and revoke all refresh tokens for the account. Existing access JWTs may live until their current short expiry unless a compatible token-version mechanism is separately approved.
- Integrate an email provider behind an interface with production and test implementations.

**Likely API and schema changes**

- Add `POST /api/v1/auth/password-recovery` with an email request and generic `202 Accepted` response.
- Add `POST /api/v1/auth/password-reset` with token and new password; return a generic success response and require a fresh login.
- Add `password_reset_tokens` with user ID, token hash, creation time, expiry time, used time, and indexes on hash/user/expiry. Optional operational metadata must be minimized and must not expose the token.
- Add configuration for reset-link base URL, expiry, sender identity, and email provider credentials.

**Compatibility notes**

- Existing register/login/refresh/logout routes and JWT claims remain unchanged.
- Recovery routes are anonymous but narrowly permitted in Spring Security and rate-limited by normalized email plus trusted client-IP policy.
- Response timing and wording should be materially similar for existing and nonexistent emails.

**Focused tests**

- Existing/nonexistent/deleted/banned account request behavior; mail-provider failure behavior; rate limits.
- Valid, expired, reused, superseded, malformed, and random tokens; concurrent reset attempts.
- Password-policy validation, BCrypt persistence, refresh-token revocation, and successful login only with the new password.
- Tests proving raw reset tokens do not appear in database rows, responses other than the email test seam, or logs.

**Acceptance criteria**

- An eligible user can complete recovery once within the configured lifetime and then authenticate with the new password.
- The same public response does not disclose account existence.
- Used/expired/superseded tokens cannot reset a password.
- All existing refresh sessions are invalid after reset, with the access-token limitation documented.
- Email and token secrets are supplied through deployment configuration, not source control.

### Phase 5 — User/subject timestamps and profile statistics

**Scope**

- Add creation/update timestamps to users and subjects, with application and database behavior defined consistently.
- Expose small, purpose-built aggregate views for profile and subject pages.
- Initial user stats: approved/latest/non-deleted post count, total upvotes received, and total downvotes received. Initial subject stats: approved/latest/non-deleted post count and last approved post time.
- Do not count pending, rejected, historical, or deleted post versions in public statistics.

**Likely API and schema changes**

- Add non-null `created_at` and `updated_at` columns to `users` and `subjects` through nullable-add → backfill → constraint migrations where required.
- Append optional `created_at` and `updated_at` fields to user and subject responses.
- Add `GET /api/v1/user/{userId}/stats` and `GET /api/v1/subject/{subjectId}/stats`, each with a dedicated response DTO.
- Prefer indexed aggregate queries over loading entity graphs. Do not persist counters until measured load demonstrates the need.

**Compatibility notes**

- Added response fields are non-breaking; all current fields remain.
- Existing records have no authoritative original creation time. Default the migration to the deployment/backfill time and label it internally as a legacy approximation unless product approves a derivation rule.
- Stats definitions are contract semantics and must be documented, especially around deletion and versioning.

**Focused tests**

- Timestamp creation/update behavior and migration backfill for legacy rows.
- Aggregate tests with multiple versions, statuses, votes, subjects, and soft-deleted posts/users.
- Authorization and not-found behavior for public profiles versus deleted users.
- Query-count or repository tests preventing N+1 loading.

**Acceptance criteria**

- Every active user and subject has non-null timestamps after migration.
- Updating an entity changes `updated_at` without changing `created_at`.
- Statistics exactly match the documented eligible post set and vote totals.
- Stats endpoints do not return entity graphs or private moderation information.
- Current user/subject consumers continue to deserialize existing fields unchanged.

### Phase 6 — User profile cover images

**Scope**

- Add one cover image per user, using the existing profile-picture flow as the behavioral baseline while consolidating shared media validation/storage code.
- Owner/admin may upload or replace; read access follows the product’s current public profile-image policy; owner/admin may remove.
- Enforce an image-only allowlist, maximum byte size, decoded dimension/pixel limits, generated filenames, and cleanup of replaced files after a successful commit.

**Likely API and schema changes**

- Add nullable `cover_picture` (or a clearly named storage key) to `users`.
- Add `PUT`, `GET`, and `DELETE /api/v1/user/{userId}/cover-picture` with multipart upload and binary read semantics parallel to profile picture.
- Append an optional cover-image reference to user responses if clients benefit from discovery; retain binary endpoint support and existing avatar fields.
- Keep storage local initially for compatibility, but hide paths behind a storage service so object storage can be adopted later without changing routes.

**Compatibility notes**

- This does not alter the current profile-picture endpoints.
- A missing cover returns the documented 404/default behavior; it must not synthesize the frontend’s current static image in the backend.
- Replacing or deleting an image must not invalidate cached avatars or unrelated attachments.

**Focused tests**

- Owner/admin/other-user upload and delete matrix; public/authenticated read policy.
- Valid image, empty file, oversize file, MIME spoof, malformed decoder input, decompression/pixel bomb, and traversal filename.
- Replacement rollback: a failed database update retains the old usable image and removes temporary data.
- Missing database reference and missing on-disk object behavior.

**Acceptance criteria**

- An authorized user can upload, replace, read, and remove a valid cover image.
- Invalid content cannot create a user reference or persistent orphan file.
- Another ordinary user cannot change the cover.
- Existing avatar and user endpoints remain compatible.
- Limits, cache headers, and storage cleanup behavior are documented.

### Phase 7 — Structured post links

**Scope**

- Represent external links separately from rich-text content so clients can render a controlled list.
- Attach links to the **post version row**, not only the logical post, so history faithfully represents each version.
- Support ordered links with URL and optional user-supplied label. Normalize for validation/deduplication while retaining an appropriate display URL.
- Allow only `http` and `https`; cap count and URL/label lengths. Do not fetch arbitrary URLs or generate server-side previews in this phase.

**Likely API and schema changes**

- Add `post_links`: ID, post row FK, URL, normalized URL/hash, optional label, position, and timestamps; add a uniqueness rule suitable for one normalized URL per version.
- Add optional `links` to post create/update requests and `PostResponse`. Omitting links preserves current behavior; an explicit empty list removes links in the newly created version.
- During post update/version creation, write the submitted link set to the new version in the same transaction.
- Existing plain URLs inside `content` remain untouched and are not automatically migrated.

**Compatibility notes**

- The request field is optional and response addition is non-breaking.
- The backend does not promise metadata titles, thumbnails, reachability, or safety of the remote destination.
- Avoid server-side URL fetching to eliminate an unnecessary SSRF surface; safe preview expansion can be designed separately if required.

**Focused tests**

- Create/update/history behavior; ordering; omitted versus empty list; duplicate normalization.
- Unsupported schemes, credentials in URLs, overlong inputs, excess count, malformed Unicode/hostnames, and script-like values.
- Transaction rollback with an invalid link and no partial post/link persistence.
- Authorization and deleted/private-version visibility inherited from the parent post.

**Acceptance criteria**

- Valid ordered links round-trip on create, read, update, and history.
- New versions cannot mutate the links attached to older versions.
- Invalid links return validation errors before persistent changes.
- No backend request is made to a submitted destination.
- Posts created by existing clients continue to work and return an empty or omitted link collection as documented.

### Phase 8 — Admin dashboard aggregates

**Scope**

- Add a read-only admin summary for operational decisions, not a general analytics platform.
- Initial measures: active/deleted users, users by role, logical posts by current status, posts created/approved in a requested period, pending moderation count, attachment count/bytes where reliably available, votes, and vault saves.
- Define one reporting timezone and inclusive/exclusive date boundaries. Return the calculation timestamp.

**Likely API and schema changes**

- Add `GET /api/v1/admin/dashboard` with optional bounded `from`/`to` parameters and a typed aggregate response grouped into users, posts, moderation, and engagement.
- Reuse current tables and indexed aggregate queries; no new table is expected initially.
- Add indexes only when query plans show they are necessary. A short in-memory cache is optional and must expose or document freshness.

**Compatibility notes**

- Endpoint is additive and `ROLE_ADMIN` only.
- Return aggregate numbers, not new collections of PII.
- Counts must use the same logical-post, version, status, and deletion definitions as Phases 1, 3, and 5.

**Focused tests**

- Security tests for anonymous/user/admin.
- Aggregate fixtures containing versions, status transitions, soft deletions, roles, votes, files, and date-boundary events.
- Empty database, large count types, timezone/DST boundaries, and invalid/unbounded ranges.
- Query-count/performance test preventing per-row aggregation.

**Acceptance criteria**

- Only admins can retrieve dashboard data.
- Counts reconcile with direct database queries using the documented definitions.
- Date windows and timezone are explicit in OpenAPI and tests.
- The endpoint performs a bounded number of aggregate queries and meets an agreed operational latency on representative data.
- No email address, token, file path, or other unnecessary personal data is returned.

### Phase 9 — Audit events

**Scope**

- Persist append-only audit events for security- and administration-relevant actions from deployment onward.
- Initial actions: login/recovery security outcomes where safe, password reset completion, role changes, user deletion, banned-email changes, post delete/moderation, administrative taxonomy changes, and cover/avatar changes.
- Store actor, action, target type/ID, time, outcome, correlation ID, and minimal structured metadata. Never store passwords, JWTs, reset tokens, refresh tokens, full post bodies, or uploaded file bytes.
- Provide paged, filtered admin read access. Application code must have no update/delete path for events.

**Likely API and schema changes**

- Add `audit_events` with immutable ID, nullable actor ID/snapshot, action, target type/ID, outcome, occurred time, correlation ID, and constrained JSON/text metadata; index time, actor, action, and target.
- Add `GET /api/v1/admin/audit-events` with page/size and allowlisted filters/date range.
- Add a transaction-aware audit service. Events representing successful mutations should commit consistently with the mutation; failed security events require a separate, explicitly safe persistence path.
- No retrospective backfill is claimed. Earlier `deleted_by`/timestamps remain provenance, not fabricated audit history.

**Compatibility notes**

- Existing mutation responses remain unchanged.
- Audit write failure policy must be action-specific and explicit: fail closed for privileged security mutations where accountability is mandatory; avoid taking down ordinary reads.
- Actor deletion must not erase historical event meaning; retain a non-sensitive actor identifier/snapshot under the approved retention policy.

**Focused tests**

- One event per audited successful action; correct actor/target/outcome/correlation fields; transaction rollback behavior.
- Failed authorization and recovery-event redaction tests.
- Admin-only paging/filtering and stable newest-first ordering.
- Secret/PII redaction tests and attempts to update/delete through repositories or APIs.

**Acceptance criteria**

- The agreed action matrix emits complete, redacted events from the release point onward.
- Events cannot be changed or deleted through application APIs.
- Admin queries are paged, bounded, authorized, and indexed.
- Sensitive credentials and content never appear in metadata or logs.
- Retention, export, access, and purge responsibilities are documented before production enablement.

### Phase 10 — Optional announcements and trending

**Scope**

- Ship only after product confirms these dynamic homepage modules are needed. Keep announcements and trending independently feature-flagged.
- Announcements: admin-authored title/body or link, publish window, priority, active state, and optional audience scope.
- Trending: a documented, deterministic score based on recent approved/non-deleted posts and time-windowed engagement, with recency decay and anti-duplication. Start with votes and post recency; do not imply view-based popularity until reliable view events exist.

**Likely API and schema changes**

- Add `announcements` with content/link, publish/start/end timestamps, priority, active state, audience scope, author, and timestamps.
- Add admin CRUD under `/api/v1/admin/announcements` and a read endpoint such as `GET /api/v1/announcements/active`.
- Add `GET /api/v1/posts/trending?window=...&limit=...`; return a bounded list using a post summary contract and optionally a transparent score/debug field for admins only.
- Trending initially uses aggregate queries or scheduled materialization only if measurement shows on-demand computation is too expensive.

**Compatibility notes**

- Both capabilities are additive. Existing static UI can remain until a frontend integration PR is ready.
- The Phase 3 `sort` contract does not silently acquire “trending”; use the dedicated endpoint until semantics and caching are stable.
- Feature flags must make disabled endpoints return a documented behavior without affecting normal post reads.

**Focused tests**

- Announcement admin authorization, scheduling boundaries, priority ordering, inactive/expired filtering, and audience rules.
- Trending fixtures for age decay, vote changes, ties, deleted/pending/historical exclusion, bounded windows, and stable ordering.
- Cache/materialization invalidation where used, plus query-plan/load tests.
- Feature-disabled behavior and no effect on existing endpoints.

**Acceptance criteria**

- Product can enable announcements and trending independently.
- Only currently eligible announcements are returned in deterministic order.
- Trending results are reproducible from the documented formula and contain only approved, latest, active posts.
- Endpoint limits prevent an unbounded query, and measured performance meets the agreed target.
- Admin announcement mutations become audited once Phase 9 is active.

## 6. Cross-cutting delivery requirements

### Database migrations

1. Introduce **Flyway** (preferred for this codebase) and establish a reviewed baseline before the first new production column. If environments differ, inventory and reconcile them before assigning a baseline version.
2. Make each migration forward-only, deterministic, and safe to rerun through deployment tooling. Never edit an applied migration; add a corrective migration.
3. Use expand/backfill/constrain sequencing for non-null columns. Separate large backfills or online index operations when table size makes a single transaction unsafe.
4. Validate migrations from both a clean database and a representative pre-roadmap schema/data snapshot. Document backup, rollout, observation, and forward-fix steps; rollback must not assume a destructive down migration.
5. After migration ownership is established, configure Hibernate to validate the schema in controlled environments rather than silently mutate production.
6. Every phase must specify data retention and foreign-key behavior for deleted users/posts. Physical media cleanup requires a reconciliation job and must not race database commits.

### OpenAPI and compatibility

- Treat the checked-in OpenAPI document and springdoc output as release artifacts. Update annotations/contracts, regenerate as applicable, lint the spec, and review a contract diff in every API PR.
- Describe all parameters, bounds, authorization requirements, statuses, error bodies, examples, and ID semantics. Explicitly distinguish database post row `id` from logical `post_id`.
- Add optional response fields; do not make existing optional request fields newly mandatory. Deprecate before removal and reserve breaking changes for a separately planned `/api/v2`.
- Preserve global `snake_case`, existing role names, JWT bearer behavior, and legacy list routes.
- Keep one standard error envelope for new work and document any legacy exceptions rather than changing old responses opportunistically.

### Testing and release gates

- Each phase needs service unit tests, MVC/controller contract tests, Spring Security authorization tests, and repository integration tests for its query/migration behavior.
- Add MySQL-compatible integration coverage (prefer Testcontainers in CI) for full-text search, indexes, constraints, JSON fields, and migration behavior that an in-memory database cannot prove.
- Add a generated OpenAPI compatibility/diff check and a migration-from-baseline CI job.
- Test versioned-post behavior explicitly: logical ID versus row ID, latest flag, moderation status, deletion, and historical visibility.
- Test concurrency for version creation, delete, password-token consumption, and other one-time operations.
- Add structured logging, correlation IDs, and metrics for new endpoints without logging credentials, post bodies, raw search content where policy forbids it, or media contents.
- Release behind feature flags only when partial enablement is useful; do not use flags as a substitute for schema/API compatibility.
- A phase is not production-ready until OpenAPI, migrations, tests, deployment configuration, and operational runbook changes are reviewed together.

## 7. Suggested PR names and merge order

Keep PRs vertically complete and merge in this order. The migration baseline is bundled with Phase 1 so feature order remains unchanged.

1. `feat/posts-soft-delete-and-flyway-baseline`
2. `security/harden-resource-authorization-and-uploads`
3. `feat/posts-search-filter-sort-pagination`
4. `feat/auth-password-recovery`
5. `feat/profile-timestamps-and-stats`
6. `feat/profile-cover-images`
7. `feat/posts-structured-links`
8. `feat/admin-dashboard-aggregates`
9. `feat/admin-audit-events`
10. `feat/optional-announcements-and-trending`

If a phase becomes too large, split it into sequential `schema`, `service`, and `api` PRs only when every intermediate deployment is backward-compatible and safe. Do not merge a public route before its authorization and contract tests.

## 8. Out-of-scope frontend integration mismatches

These issues should be tracked in frontend work; backend phases should document the contract but should not modify the frontend repository:

- The create-post screen displays “Save Draft” but has no IndexedDB persistence wiring. Implementing the decision in Section 4 is frontend work.
- Current sort controls log selections rather than query the backend. Search/filter parameter binding, page navigation, loading/error/empty states, and adoption of `/posts/search` are frontend work.
- Announcements and trending components contain hard-coded examples. Replacing them with Phase 10 API data is a separate optional frontend integration.
- The create-post links/video/image areas are currently presentation examples rather than a complete API submission flow. Phase 7 supplies a link contract but does not wire those controls.
- The frontend title input allows 128 characters while the backend currently validates a 30-character maximum. Product must choose a limit; aligning validation and user feedback requires a frontend change even if the backend limit later expands additively.
- The frontend pending-department constant uses a path that does not match the backend’s `/api/v1/posts/pending/department/{departmentId}` route. Correct the client rather than adding an ambiguous duplicate backend route unless telemetry proves deployed clients rely on it.
- The frontend vault check base omits the backend’s required `/{postId}` segment; callers must construct the documented route consistently.
- Static profile covers/counts do not become dynamic merely because Phases 5–6 exist; client rendering, fallbacks, cache busting, and upload UX are separate.
- Attachment upload accepts a post database row ID while post versioning also exposes logical `post_id`. Backend documentation/tests will clarify the distinction, but frontend call sites must use the row ID intentionally.
- Password recovery pages, success/error messaging, and deep-link handling are frontend scope after Phase 4 defines the API.

## 9. Open product decisions

Defaults above permit engineering estimates, but these decisions should be resolved no later than the named phase:

| Decision | Needed by | Proposed default |
|---|---:|---|
| Post deletion retention, restore rights, and eventual file purge | Phase 1 | Indefinite soft retention initially; no restore API; owner/admin delete; files retained |
| Whether inaccessible resources return 403 or conceal existence with 404 | Phase 2 | 404 for guessed private/deleted resources, 403 for an authenticated visible resource with a forbidden mutation |
| Upload types, byte limits, image pixel limits, and malware scanning requirement | Phase 2 | Small allowlist and conservative limits; quarantine/scanning before broad document support |
| Search matching language/collation and whether content is included | Phase 3 | MySQL natural-language full-text over title and content using the production collation |
| Public stats visibility and exact count labels | Phase 5 | Public aggregate counts only; latest approved non-deleted logical posts |
| Meaning of legacy user/subject `created_at` | Phase 5 | Migration time, explicitly treated as approximate for legacy rows |
| Cover-image dimensions/cropping and long-term storage provider | Phase 6 | Preserve original within pixel/byte limits; client crops; current storage behind an abstraction |
| Link labels, maximum links, and metadata-preview ownership | Phase 7 | URL plus optional label, maximum 10, no backend fetching |
| Dashboard reporting timezone, ranges, freshness, and latency target | Phase 8 | UTC, bounded 90-day optional range, calculation timestamp returned, short cache only if measured |
| Audit event matrix, retention, export, and who may view it | Phase 9 | Admin-only, append-only, minimum necessary metadata, retention approved before launch |
| Announcement audience model and trending formula/window | Phase 10 | Global announcements first; 7-day time-decayed vote score; feature flags off by default |
| Shared-device behavior for IndexedDB drafts on logout | Frontend draft delivery | Keep per-user records but hide them from other accounts; offer explicit “remove local drafts” on logout |

## 10. Roadmap completion outcome

When all required phases are complete, Arca will retain its current Spring MVC/JPA/JWT architecture and `/api/v1` compatibility while adding safe post deletion, consistent resource security, scalable post discovery, account recovery, richer profile data/media, structured links, operational aggregates, and auditability. Announcements and trending remain explicitly optional, and drafts remain a deliberate frontend-only capability until product requirements justify server persistence.

### Final handoff reminder — requested by user

At the final roadmap handoff, explicitly remind the user:

> Add V5 token/rate tables. Recovery stays disabled until SMTP, frontend URL and a separate rate-key secret are configured.

V5 migration is already implemented; the remaining deployment action is to apply it through the reviewed rollout procedure and configure/enable recovery. Refer to [Phase 4 operations](phase-4-operations.md). Do not treat committed backend code as confirmation that production recovery is enabled.
