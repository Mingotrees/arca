# Phase 10 — Announcements and trending

## Direction and alternatives
User confirmed both optional homepage modules and timestamped votes; deployment follows feature verification. Selected on-demand indexed JDBC queries using existing MVC/JPA/JWT stack. Snapshot ranking with all-time votes was rejected because it cannot measure recent engagement. Scheduled materialization/cache is deferred until measurements justify it.

## Shared constraints
- Additive V11 only; V1–V10 unchanged. Independent ANNOUNCEMENTS_ENABLED / TRENDING_ENABLED flags default false. Authorized requests to disabled modules return JSON 404; authentication/validation still apply normally. All reads remain authenticated, preserving existing JWT policy. No frontend changes, external fetching, view events, cache or background publication job.
- UTC DATETIME(6), microsecond precision, years 1000–9999; reject unsupported timestamp precision/ranges rather than silently round.
- Announcement writes use authenticated numeric actor snapshots without user FK and commit with Phase 9 audit events. Admin role required at HTTP and service boundaries.

## Announcements
Global audience only. Admin POST/GET-list under /api/v1/admin/announcements and GET/PUT/DELETE /{id}; GET /api/v1/announcements/active for authenticated users. Create/replace request: required title <=128 characters; optional plain-text body <=4096; optional HTTP(S) URL <=2048 using existing post-link validation; body or URL required after trimming. Priority 0–1000 defaults 0; active defaults false. Optional starts_at/ends_at; start inclusive, end exclusive, end must exceed start when both supplied. Null start means immediately eligible when active, null end means no expiry. No audience targeting or HTML rendering promise; clients render body/title as text.

V11 announcements: generated ID, title/body/url, priority, active, starts_at/ends_at, created_by/updated_by numeric snapshots, created_at/updated_at, is_deleted and deleted_at. Soft deletion retains rows, hides from all reads, emits one ANNOUNCEMENT_DELETED event; repeated deletion returns 204 without a second event. Missing IDs return 404. No restore/purge API. Updates are full replacements with defaults, not PATCH; serialize update/delete via row lock. CREATED/UPDATED/DELETED events target ANNOUNCEMENT row ID, metadata {}. Identical successful update commands may emit an UPDATED event.

Public DTO: id/title/body/url/priority/starts_at/ends_at. Admin DTO additionally active/created_by/updated_by/created_at/updated_at. Admin list is paged, default page0/size20, max page10000/size100, stable created_at DESC,id DESC. Active list bounded default10/max50, stable priority DESC, COALESCE(starts_at,created_at) DESC,id DESC.

## Trending
GET /api/v1/posts/trending?window=7d&limit=10; windows 24h/7d/30d, max limit50. Return calculated_at/window/content. Summaries explicitly contain row id, logical post_id, title, user_id, department_id, approved_at, recent_upvotes/recent_downvotes; no entity graphs, paths, private content or admin-only score.

V11 adds nullable votes.changed_at without legacy backfill. New and switched votes receive UTC timestamp; same-type toggle/removal deletes the vote as before. Legacy-null dates never count as recent; no immutable vote event history is claimed. One current vote per account/post-version remains enforced by existing unique key and logical-post lock. Toggling cannot accumulate multiple votes, but is not a complete anti-abuse system.

At one calculation instant t, candidates are approved/latest/non-deleted version rows with known approved_at in [t-window,t). If inconsistent legacy flags identify multiple eligible versions, choose highest version per logical post. Count only currently held votes on that version with changed_at in the same window; exclude future/unknown dates, retain votes by deleted users consistent with profile statistics. Revisions do not inherit historical votes.

Score = (1 + recent_upvotes) / ((1 + recent_downvotes) * (2 + age_hours)^1.5), where age_hours = elapsed UTC time since approved_at at t. Order score DESC,approved_at DESC,id DESC. Baseline allows zero-vote recent posts, downvotes lower rank, recency decays rank. Return calculation time so formula is reproducible; ordering can evolve with time even without writes. Legacy approvals with null dates are excluded instead of treating local-clock created_at as authoritative UTC. No trending sort alias is added to search.

Indexes support announcement active/list queries, recent approved post candidates and vote changed_at/post/type aggregation. Measure bounded query count/plans on synthetic corpus. V11 migration, flag combinations, UTC schedule boundaries, ranking/vote transitions, visibility/deduplication, audits/rollback, OpenAPI parity and full suite must pass before deployment preparation. No commit/push without request.
