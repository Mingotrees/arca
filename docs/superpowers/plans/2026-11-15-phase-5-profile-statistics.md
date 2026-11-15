# Phase 5 Profile Timestamps and Statistics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add reliable user/subject timestamps and bounded profile aggregates while preserving existing API/authentication contracts.

**Architecture:** V6 expands/backfills/constrains metadata columns with UTC database defaults. ORM lifecycle callbacks maintain UTC modification values; association-only mutators explicitly touch the parent. A read-only statistics service checks target existence then executes one indexed aggregate query per endpoint without materializing post graphs.

**Tech Stack:** Java 21, Spring Boot 3.5.5, Hibernate/JPA/JDBC, MySQL 8.4/Flyway, JUnit/Testcontainers.

**Spec:** `docs/arca-backend-roadmap.md` Phase 5 and cross-cutting delivery requirements; prior user delegation applies to successive roadmap delivery.

## Global Constraints

- Branch `feat/profile-timestamps-and-stats` starts at merged Phase 4 `66afd10`. Preserve the user's existing final-handoff reminder edit in the roadmap. No commit/push/production migration unless requested.
- Keep legacy response fields/routes, snake_case, roles, JWT/session behavior and resource visibility. New stats require existing authenticated profile/taxonomy access; their contents are public aggregates, not private moderation data.
- Add optional created_at/updated_at to user/subject response DTOs. New metadata is UTC ISO instant (`Z`) with microsecond precision, not a reinterpretation of existing post timestamps.
- V6: nullable add → UTC backfill → NOT NULL UTC defaults for users/subjects; `timestamps_approximate` marks preexisting rows internally. New rows default false. Creation time and approximation provenance remain immutable across normal updates.
- ORM callbacks maintain updated_at in UTC, monotonically at microsecond resolution, with creation/provenance fields excluded from updates. Role/subject-alias/department-only mutators explicitly dirty the timestamp. Direct SQL maintenance must explicitly stamp the parent updated_at; soft deletion includes UTC stamping in its existing custom SQL.
- User stats: approved_post_count counts distinct logical post_id among APPROVED, latest-flagged, non-deleted rows; upvotes_received/downvotes_received count matching stored votes on those eligible version rows.
- Subject stats: same post eligibility and logical count, using EXISTS membership; last_approved_post_at is MAX(updated_at) of eligible rows (existing approval timestamp clock), null when empty. Do not invent retrospective approval times.
- Deleted/missing users and missing subjects return generic 404 for every role. Invalid nonpositive IDs return 400. Owners/admins receive no extra pending/history/deleted statistics.
- Retained votes from deleted voters and eligible posts by deleted authors follow existing post/count semantics; deleted users' own stats remain inaccessible. No persisted counters or new analytics store.
- Keep count fields int64, empty totals zero, preserve existing PostResponse/search eligibility, and use constant query count independent of volume.

## Review Focus

- Pending/rejected/superseded/nonlatest/deleted versions and multiplying subject/vote joins must not inflate aggregates.
- Metadata must survive profile/avatar/password/role updates and relation-only subject changes without allowing client-supplied creation times or resetting provenance.
- UTC defaults and explicit SQL maintenance stamping must work with non-UTC database sessions; legacy timestamps remain explicitly approximate.
- Deleted-profile existence must not leak through aggregate queries; DTOs must not expose entity graphs, passwords, roles beyond existing contracts, or provenance fields.
- V1–V5 checksums, recovery/session races, search query-count bounds and final recovery-configuration reminder must remain intact.

---

### Task 1: Regression contract and migration foundation

**Files:** profile timestamp/statistics integration and migration tests; `V6__profile_timestamps.sql`; User/Subject metadata mappings.

- [x] Add failing timestamp/read-only-input/backfill and authenticated aggregate endpoint regressions; observe RED.
- [x] Implement expand/backfill/constrain metadata with internal legacy marker and UTC defaults; verify clean/V5 upgrade preservation.
- [x] Map callback-maintained timestamps and explicitly touch relation-only application updates; verify immutable creation time, monotonic updates and exact microsecond round trips.

### Task 2: Aggregate service and additive response DTOs

**Files:** statistics repository/service/controller; user/subject response DTOs and subject mapper; existing role/subject update services.

**Interfaces:** `ProfileStatisticsService.userStats(Long) : UserStatsResponse`, `subjectStats(Long) : SubjectStatsResponse`. One existence query and one JDBC aggregate per call, in read-only repeatable-read transaction.

- [x] Implement bound-ID aggregate SQL, shared eligible-post predicates and purpose-built int64 response DTOs; preserve null last-approval time for empty subjects.
- [x] Add user/subject stats routes, existing JWT access expectations, typed errors and optional metadata fields without changing existing fields.
- [x] Verify mixed versions/statuses/taxonomy/votes/deleted users, logical-ID versus row-ID semantics and empty/not-found/authorization cases.
- [x] Demonstrate bounded query count and observed aggregate indexes on representative fixtures.

### Task 3: Contract, observations, rollout and independent review

**Files:** controller/DTO annotations, `openapi.yaml`, README/API notes, `docs/phase-5-operations.md`, observation and contract tests.

- [x] Add content-free stats correlation/latency metrics and preserve earlier event names.
- [x] Document UTC/legacy approximation, explicit SQL/association-touch behavior, exact count/approval definitions, backfill rollout and retained deletion semantics.
- [x] Check generated/checked-in operations/schemas and actual wire payloads, then full Maven verify, OpenAPI lint and diff checks.
- [x] Obtain independent review, reproduce/fix required findings and record final evidence; retain final-handoff reminder for roadmap completion.

## Execution ledger

- Branch check: develop includes Phase 4 via `66afd10`; only existing working-tree edit was the user-requested final recovery-configuration reminder. Created next phase branch and preserved that edit.
- Ruling: execute inline using roadmap defaults and existing JWT profile access — prior user delegation covers remaining design choices — anonymous statistics would be a separate access-policy change, while all authenticated users can read eligible aggregates.
- Initial proposal: database-generated UTC metadata with update triggers. Migration testing rejected this approach under ordinary MySQL application privileges; see ruling below.
- Ruling: last_approved_post_at uses retained Post.updated_at — approval already records that value and no separate authoritative approved_at exists — historical clock/provenance limitations remain documented rather than fabricating times.
- Pre-flight: metadata callbacks must remain compatible with User DynamicUpdate and reset/avatar locking; aggregates must reuse approved/latest/non-deleted eligibility and avoid association materialization. New metadata DTO fields need explicit snake_case and optional null handling for old clients/tests.
- Initial RED: missing stats route returned 500 instead of an aggregate; profile responses lacked timestamps; clean migration applied five versions instead of six.
- Ruling: use UTC lifecycle callbacks plus UTC SQL defaults instead of triggers — clean MySQL 8.4 migration failed with error 1419 (binary logging requires elevated global privileges for trigger creation) under the existing database user — direct SQL maintenance must explicitly set updated_at, while all API/soft-delete and association mutation paths are handled without expanding privileges. Failed trigger prototype ran only against disposable databases and was never deployed.
- Ruling: expose new metadata as UTC instants with Z, while keeping last_approved_post_at in the existing post timestamp clock — new user/subject fields have no legacy wire-format constraint and should be unambiguous — clients must distinguish new UTC metadata from retained post-local timestamps.
- Core GREEN: role/state/empty/deleted/subject-intersection aggregates, additive metadata, scalar/relation-only updates and non-UTC SQL defaults pass. MySQL session Com_select measurement confirms exactly two SELECTs per stats call before and after adding 1,000 posts, 2,000 subject links and 1,000 votes.
- Representative 10,000-post EXPLAIN: user aggregate uses posts.idx_user_id and votes.uk_votes_post_user (ref); subject aggregate uses post_tags.idx_post_tags_subject_post (ref) then posts.PRIMARY (eq_ref). Raw plans saved to target/phase-5-query-plans.json. Fixture validates logical count 100 and received votes 66 up/34 down for author 2.
- Soft-delete monotonicity regression first failed when an existing UTC timestamp was ahead of database now; custom soft-delete SQL now uses GREATEST(now, previous+1 microsecond). Creation time/provenance stay intact.
- Observability RED: new stats routes lacked X-Request-ID/timers. Extend existing pre-security filter with content-free user_stats/subject_stats events while retaining earlier event/metric names.
- Observability GREEN: authenticated stats, sanitized anonymous correlations, separate latency/outcome meters and content-free logs pass. OpenAPI stats operations, user/summary/subject timestamps, nullable last approval and corrected existing department_ids request field match generated artifacts; pinned lint passes with 135 existing warnings.
- V6 clean/baseline/V5 upgrade migration regressions pass with ordinary schema credentials. Legacy active/deleted users and subjects receive non-null UTC backfill with internal approximation flags; passwords/sessions stay unchanged; new SQL rows default exact provenance and repeated migrate is a no-op.
- Full verification before review: initial full command was interrupted and not counted as a pass. Rerun `./mvnw --batch-mode verify -Dlogging.level.root=WARN -DtrimStackTrace=true` → BUILD SUCCESS, 216 tests, zero failures/errors/skips, executable JAR built. OpenAPI parity passed; pinned Redocly minimal lint passed with 135 existing warnings; `git diff --check` passed. Earlier recovery/session/avatar races and search query-count tests remain green.
- Independent read-only review completed with no Critical, Important or actionable Minor findings; no declined-to-judge items. It confirmed V1–V5 unchanged, additive contracts, privacy/eligibility rules, ordinary-privilege migration and preserved final reminder. No findings deferred.
- Packaged-JAR smoke against disposable MySQL through production MariaDB driver passed with real JWTs: physical/wire UTC metadata, ignored creation-time input, relation-only timestamp updates, authenticated empty/approved/voted aggregates, approval-clock consistency and deleted-profile/retained-author behavior. Java process, temporary storage and container removed; no existing database or real mailbox accessed.
- All tasks complete. Working changes retained uncommitted on requested Phase 5 branch; user-requested final recovery-configuration reminder preserved for final roadmap handoff. No push or production migration performed.
