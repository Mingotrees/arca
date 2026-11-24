# Phase 5 — Profile timestamps and statistics

## API

Existing authenticated user/subject reads retain their fields and append optional, read-only `created_at` and `updated_at`. User search summaries also carry these timestamps without adding email/roles. New metadata is emitted as **UTC ISO instants with `Z`**, at microsecond precision. It is not a reinterpretation of legacy post clocks. Missing optional timestamps on an unpersisted/internal DTO are omitted; existing response fields retain their null/serialization behavior.

New endpoints require the same ROLE_USER/ROLE_ADMIN bearer access as existing profiles/taxonomy. “Public statistics” means no private moderation counts, not new anonymous access:

| Endpoint | Response |
| --- | --- |
| `GET /api/v1/user/{userId}/stats` | `approved_post_count`, `upvotes_received`, `downvotes_received` |
| `GET /api/v1/subject/{subjectId}/stats` | `approved_post_count`, `last_approved_post_at` |

All counts are nonnegative int64 aggregates. Missing/deleted users and missing subjects return generic JSON 404 for every role. Nonpositive or malformed IDs return JSON 400; anonymous calls return 401 and callers without profile roles return 403. Empty active users receive zero counts; empty subjects receive count 0 and explicit `last_approved_post_at:null`. No posts, user/entity graphs, passwords, reset tokens or private status totals are returned.

### Exact eligibility and ID semantics

- Eligible version rows satisfy APPROVED status, `is_latest_version=true`, `is_deleted=false`, matching current post discovery. Owner/admin privileges do not widen this set.
- `approved_post_count` counts distinct logical `posts.post_id`, not version-row IDs. Multiple subjects/votes and even legacy duplicate eligible latest flags cannot multiply the logical count.
- User votes are counts of stored UPVOTE/DOWNVOTE rows attached to eligible **version-row IDs**. Retained votes from deleted voters still count, as in existing vote-count APIs. Other vote types do not contribute. If legacy data has multiple eligible version rows for one logical identity, logical count remains one while vote totals cover those eligible rows; reconcile inconsistent flags using existing version rules rather than silently changing discovery semantics here.
- Subject membership uses EXISTS against post_tags. An eligible version counts once for each subject it actually links to, without multiplying when it links to other subjects.
- `last_approved_post_at` is MAX of eligible Post.updated_at. Approval already records this value; there is no separate authoritative approved_at to backfill. It follows the existing **stored post wall clock** and local date-time representation, unlike new UTC user/subject metadata. Do not infer an offset or manufacture retrospective approval history.
- Logical soft deletion removes every version and associated retained votes from the eligible aggregate set without purging data. A soft-deleted author's own profile/stats return 404, but their still-active approved posts remain in subject statistics, consistent with current post visibility. Deleted authors are not implicitly a post deletion.

Examples:

```json
{"approved_post_count":2,"upvotes_received":3,"downvotes_received":1}
```

```json
{"approved_post_count":0,"last_approved_post_at":null}
```

## V6 migration and legacy approximation

V1–V5 remain unchanged. V6 adds nullable created_at/updated_at to users and subjects, backfills one UTC deployment timestamp, then constrains both columns NOT NULL with UTC SQL defaults. Internal `timestamps_approximate=true` identifies every preexisting row, including deleted users. Original creation times were not stored: migration time is an approximation, not a claimed historical join date. New rows default false. The provenance field is internal and absent from public DTOs; schema descriptions document legacy approximation.

UTC row metadata is stored in DATETIME(6), with ORM callbacks setting UTC values truncated to microseconds. Creation/provenance fields are excluded from normal ORM updates and ignored in raw entity JSON. Scalar updates invoke the modification callback; role, alias and department mutators explicitly dirty the parent timestamp for association-only writes. User DynamicUpdate remains enabled, so timestamp support does not reintroduce stale avatar/password-field overwrites. Soft-delete custom SQL stamps updated_at explicitly and clamps against the stored value. Callbacks advance beyond the value loaded by that entity when its clock has not advanced; synchronize application/database clocks. These timestamps are not optimistic-lock versions or a global commit-order guarantee across stale snapshots/unsynchronized nodes. Post/vote activity does not itself modify profile timestamps; statistics are computed separately.

Direct SQL insertions can omit metadata and receive UTC defaults even under non-UTC sessions. Direct SQL maintenance must preserve creation/provenance and explicitly set `updated_at=UTC_TIMESTAMP(6)` (or `GREATEST(UTC_TIMESTAMP(6),updated_at + INTERVAL 1 MICROSECOND)` when monotonicity matters); join-only maintenance must touch its parent as well. Application callbacks cannot observe SQL executed outside ORM. Imported legacy records require an explicitly reviewed provenance choice.

An initial trigger prototype was rejected by ordinary MySQL 8.4 credentials with binary logging (error 1419 requiring elevated privileges). Final V6 intentionally contains **no triggers**, no global variable changes and no connection time-zone changes. Do not grant SUPER or weaken binary-log policy to enable this phase.

Rollout:

1. Inventory users/subjects sizes, prior schema history, metadata-column name conflicts and long transactions; back up and rehearse V5→V6 on a restored production-sized copy.
2. Budget row-update/DDL locking, binlog/replica load and disk. Full-table legacy backfill is explicit; do not assume a large deployment can perform it without a write-maintenance window or an operator-reviewed staged/batched migration.
3. Quiesce user/subject writes while applying V6 and deploying timestamp-aware writers. Old binaries tolerate additive columns/defaults, but association-only writes from old nodes do not touch metadata; do not claim complete modification tracking during mixed-version writes.
4. Verify all user/subject rows have timestamps, preexisting rows are marked approximate, passwords/sessions/recovery tables and prior post data remain intact. Exercise profile, avatar/password/role and subject relation updates before resuming writes.
5. For interruption, inspect schema/backfill/constraints and use reviewed Flyway reconciliation/forward-fix procedures. Never edit applied V1–V5 migrations or blindly rerun partially applied ALTERs. This change does not apply production migrations automatically during development.

## Aggregate queries and observed evidence

Each stats request uses **two SELECTs**: target existence, then one aggregate, in a read-only repeatable-read transaction. There is no post graph loading, per-post query, stored counter, cache, event history or new analytics table. Invalid IDs use no aggregate; missing/deleted targets stop at existence check.

MySQL session Com_select measurements confirm two SELECTs for user and subject endpoints both before and after adding 1,000 posts, 2,000 subject links and 1,000 votes. A separate synthetic 10,000-post corpus with 100 authors/subjects, 20,000 links and 1,000 votes produced observed JSON EXPLAIN plans:

| Aggregate | Observed indexed access |
| --- | --- |
| User | posts.idx_user_id ref lookup; votes.uk_votes_post_user ref lookup |
| Subject | post_tags.idx_post_tags_subject_post ref lookup, then posts.PRIMARY eq_ref |

Raw output is written to `target/phase-5-query-plans.json` by PostSearchQueryPlanTest. These are observed synthetic plans, not production latency/capacity guarantees; existing indexes suffice for the measured workload. Native JDBC counters, rather than only Hibernate statistics, verify the full select budget.

Stats return sanitized/generated X-Request-ID and emit content-free `user_stats` / `subject_stats` events and `arca.profile.user.stats` / `arca.profile.subject.stats` timers by status/outcome. They contain no raw IDs/paths, profile data, post content or credentials. Earlier post/recovery event/metric names and admin-only Actuator access remain supported.

Verification includes clean/baseline/V5→V6 preservation, UTC under non-UTC sessions, ignored client timestamp input, immutable creation/provenance, role/relation-only updates, soft-delete monotonicity, mixed version/status/vote/subject/deleted-user aggregates, empty/null results, authorization, bounded query counts, observed indexes and OpenAPI parity. Run full Maven verify and pinned README OpenAPI lint before release.
