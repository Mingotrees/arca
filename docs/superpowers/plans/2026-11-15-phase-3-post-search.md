# Phase 3 Post Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add authenticated, composable post discovery without changing legacy list routes.

**Architecture:** A validated search criteria record feeds one parameterized MySQL query builder. Page/count queries select row IDs, then bounded batched JPA fetches and vote aggregates populate existing PostResponse objects in ranked order within a read-only transaction.

**Tech Stack:** Java 21, Spring Boot 3.5.5, JDBC/JPA, MySQL 8.4, Flyway, JUnit/Testcontainers.

**Spec:** `docs/arca-backend-roadmap.md`, Phase 3 and cross-cutting requirements. User's prior delegation to proceed applies to subsequent roadmap delivery.

## Global Constraints

- Branch `feat/posts-search-filter-sort-pagination` starts from merged Phase 2 (`79dae12` on develop). No commit/push/deployment unless requested.
- Preserve legacy list routes, successful payloads, JWT expectations and snake_case serialization. Search uses a new `GET /api/v1/posts/search` page envelope and existing PostResponse content.
- Default results: APPROVED, is_latest_version=true, non-deleted, for every role. Explicit status parameter requires admin. Admin non-approved searches select the newest stored revision per logical post, including a rejected revision whose latest flag is cleared. Supported statuses: APPROVED, PENDING_APPROVAL, REJECTED, SUPERSEDED.
- Filters intersect: q (trimmed, blank means absent, max 200 characters), positive subject_id/department_id/author_id, optional `from` inclusive and `to` exclusive on created_at. ISO local date-times match existing timezone-naive stored/serialized timestamps; do not silently assume historical timestamps are UTC.
- Zero-based page >=0, size 1–100 (default 20). Sort allowlist: newest, oldest, relevance, vote_score. Default relevance with nonblank q, otherwise newest. Relevance requires q. created_at determines newest/oldest; every sort uses row-ID tie-breaker.
- MySQL built-in natural-language full-text parser over title/content, utf8mb4_0900_ai_ci collation, default InnoDB token/stopword settings documented. No LIKE fallback, Boolean-query operators, trending or frontend changes.
- Forward-only V4 migration; preserve V1–V3 checksums. Index DDL requires a maintenance/backup/rehearsal runbook; never apply to existing databases during this session.

## Review Focus

- Owner/admin privileges must not leak non-approved/historical/deleted rows into default discovery; explicit status must not be user-controlled privilege escalation.
- Subject joins must not multiply rows/totals; ordering must survive ties and entity fetch order changes.
- Query strings, sort names and IDs must not become SQL syntax; overflow and out-of-range paging must not trigger unbounded fetches.
- Full-text matching uses committed data and parser/collation rules, not substring assumptions; title/content must use the same explicit collation.
- Page mapping must not add per-result vote/file/subject queries, silently hide failures, or break legacy DTO fields.

---

### Task 1: Search contract and integration regressions

**Files:** `src/test/java/com/popman/arca/PostSearchIntegrationTest.java`, existing migration tests; new criteria/page DTOs, post service/controller.

**Interfaces:** `PostSearchCriteria` validates/normalizes raw query parameters; `PostSearchResponse(List<PostResponse> content, int page, int size, long totalElements, long totalPages, String sort)` explicitly documents snake_case names.

- [x] Add MySQL/MVC fixtures covering every filter, combinations, roles/statuses, stable newest/oldest/vote/relevance order, empty/last pages, invalid bounds and legacy arrays.
- [x] Run regressions and observe missing-route/missing-index failures.

### Task 2: Migration, query and batched mapping

**Files:** `V4__post_search_indexes.sql`, `repository/PostSearchRepository.java`, post/vote repositories, post service implementation, DTOs/controller.

**Interfaces:** repository `search(PostSearchCriteria): SearchResult(List<Long> ids, long total)`; builder returns bound SQL/parameters reused by count/page and EXPLAIN checks. JPA batch methods fetch authors/departments/subjects and files separately; grouped vote counts feed a mapper overload.

- [x] Add explicit text collation/FULLTEXT and created-time/author/department/subject indexes through V4; verify clean and legacy upgrade preservation.
- [x] Implement parameterized intersection predicates, EXISTS subject filter, literal allowlisted ordering, deterministic IDs and long offsets.
- [x] Add bounded batch fetches and vote aggregates; preserve ranked ID order and full PostResponse payloads.
- [x] Run focused integration/migration/deletion/security regressions and observe GREEN.

### Task 3: Operational/contract verification and review

**Files:** `openapi.yaml`, controller/DTO annotations, README/API notes, `docs/phase-3-operations.md`, contract and query-plan tests.

- [x] Exercise actual query plans on a synthetic 10,000-post MySQL fixture; record observed indexes, full-text access and vote-sort cost without inventing production measurements.
- [x] Document tokenizer/collation, date and admin-status semantics, index rollout, pagination consistency and size/sort limits.
- [x] Verify generated/checked-in search operation and schemas, actual JSON page binding and unchanged legacy routes.
- [x] Run full Maven verify, pinned OpenAPI lint and diff checks; obtain independent review, reproduce/fix important findings, and record final evidence.

## Execution ledger

- Branch check: working tree clean, develop contains Phase 2 via merge `79dae12`; created requested feature branch from that commit.
- Ruling: execute inline using existing stack and prior delegated design choices — roadmap fixes next deliverable and user asks to start it — keep work uncommitted pending explicit integration instructions.
- Pre-flight: query eligibility/count and page fetch must share predicates; mapper must consume batched vote counts rather than legacy per-row methods. Migration collation and full-text columns must match the query MATCH expression. Contract enum/default/date semantics must match criteria validation.
- Task 1 RED: missing search route returned 400 instead of a page; clean migration ran only V1–V3 instead of V1–V4. New tests cover filter/status/role/paging/sort/Unicode/date/legacy behavior.
- Task 2: migration clean bootstrap/legacy upgrade tests pass; 26 search integration cases pass. Natural-language fixture ranking initially tied at zero because corpus statistics were stale after fixture replacement; ANALYZE TABLE refreshed scores (row 10 ≈0.05956, row 11 ≈0.01191). Only fixture statistics handling changed; production uses native MySQL ranking.
- Query-plan fixture: 10,000 posts, 100 authors, 10 departments, 100 subjects, 20,000 subject links, 1,000 votes and 200 attachments. Initial autocommit seed exceeded tool timeout; wrapping fixture insertion in one transaction reduced test setup to seconds and preserves committed full-text visibility.
- Observed MySQL plans: newest uses idx_posts_search_created (range); department/author use respective composite indexes (ref); subject uses idx_post_tags_subject_post then posts PRIMARY; text uses ft_posts_title_content (fulltext); vote_score uses indexed uk_votes_post_user dependent lookups and a sort. Page mapping executes 3 JPA statements at both sizes 1 and 100, plus 2 JDBC statements for count/page. Raw plans saved to target/phase-3-query-plans.json.
- Ruling: admin non-approved searches select MAX(version) per active logical post — rejection clears the latest flag, so relying on that flag would hide the current rejected revision — this differs intentionally from default approved/latest discovery and is documented.
- Ruling: from/to use existing serialized local created_at values without inventing UTC provenance — historical timestamp storage is timezone-naive — clients must use the same stored clock interpretation until a separately planned timestamp migration.
- Observability RED: search lacked X-Request-ID and search outcome timer. Extend existing pre-security observation filter while preserving deletion event names and omitting all raw search input.
- Observability GREEN: correlation/header, success/rejection meters and content-free logs pass. OpenAPI drift test first found missing search operation, then passed after adding operation/schema and explicit integer parameter types/defaults.
- Date precision RED: nanosecond filter input returned 200 although DATETIME(6) cannot represent its boundary; criteria now reject sub-microsecond precision rather than silently round/truncate it.
- Full verification before review: `./mvnw --batch-mode verify -Dlogging.level.root=WARN -DtrimStackTrace=true` → BUILD SUCCESS, 154 tests, zero failures/errors/skips, executable JAR built. This includes sub-microsecond rejection GREEN and V3→V4 preservation of Unicode text, deletion provenance, files and votes. Pinned Redocly minimal lint passed with the same 135 warnings; `git diff --check` passed. MySQL plan choice varied legitimately with statistics (range/ref newest, status/active index for score), so runbook records both observed forms.
- Independent review: no Critical or Important runtime/security findings. It identified missing typed error-response documentation and insufficient newest-stored-revision regression fixtures.
- Final: Ruling: treat the two review follow-ups as required release-contract/coverage work — cross-cutting roadmap requires error bodies, and stale moderation matches can mislead administrators — add a shared error DTO/schema and stronger multi-revision fixtures; no successful response or query semantics change.
- Error contract RED: `searchErrorBodiesMatchDocumentedJsonEnvelope` observed that documented 400 lacked application/json although runtime returns it. Add required timestamp/status/error/message schema shared by 400/401/403. New moderation regression verifies an older pending/rejected/superseded row never resurfaces below a newer differently-statused revision, including totals.
- Packaged-artifact smoke passed against disposable MySQL using production MariaDB driver: real JWT login, create/moderate posts, full-text pages, vote-score counts, admin pending search, nonadmin status denial/default concealment, parameter bounds and request correlation. Existing WebP/CORS/real HTTP multipart 413 smoke also passed. Java process, temporary storage and database container removed; no existing database accessed.
- Final: Ruling: production DDL duration/capacity remains an operator rehearsal requirement — synthetic EXPLAIN is not a production benchmark — maintenance-window/disk requirements cannot be inferred from the test corpus; runbook explicitly requires restored-data validation.
- Final fix verification: `searchErrorBodiesMatchDocumentedJsonEnvelope` observed RED→GREEN; generated/checked-in `ApiErrorResponse` and search operation match, and real 400/401/403 bodies satisfy required fields. Stronger multi-revision moderation content/totals regression passes. No review findings deferred.
- Final full verification: `./mvnw --batch-mode verify -Dlogging.level.root=WARN -DtrimStackTrace=true` → BUILD SUCCESS, 156 tests, zero failures/errors/skips, executable JAR built. Redocly 2.78.0 minimal lint passed with 135 pre-existing warnings; `git diff --check` passed. All tasks complete; changes retained uncommitted on requested Phase 3 branch. No existing database migration, commit, push or deployment performed.
