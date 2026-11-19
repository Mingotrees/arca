# Phase 10 — Announcements and trending

## Flags and authorization

- `ANNOUNCEMENTS_ENABLED=false` (`app.homepage.announcements-enabled`) controls all announcement routes independently.
- `TRENDING_ENABLED=false` (`app.homepage.trending-enabled`) controls trending independently.

Both default off. Authorized callers receive standard JSON 404 while a module is disabled; authentication and request binding/validation still run normally (401/403/400 can precede the feature gate). Controllers remain registered and documented even when disabled. Existing post lists/search/sorts, votes, profile routes and JWT contracts remain available. Reads require the existing authenticated USER/ADMIN role policy; global audience does not mean anonymous access. Admin announcement methods also enforce method-level ADMIN authorization. Flags are deployment configuration, not an admin toggle API.

## Announcements

Routes:

- `POST /api/v1/admin/announcements` → 201, body plus Location header.
- `GET /api/v1/admin/announcements?page=0&size=20` → page envelope, maximum page10000/size100, newest creation then ID descending.
- `GET /api/v1/admin/announcements/{id}` → admin view.
- `PUT /api/v1/admin/announcements/{id}` → full replacement, not PATCH.
- `DELETE /api/v1/admin/announcements/{id}` → 204, soft deletion. Repeating deletion of an existing row returns 204 without another event; missing IDs return 404. Deleted rows are hidden from both admin and ordinary reads; no restore/purge API.
- `GET /api/v1/announcements/active?limit=10` → bounded array, limit1–50, global signed-in audience.

Example creation:

```json
{
  "title": "Library maintenance",
  "body": "Downloads may be interrupted during the maintenance window.",
  "url": "https://example.org/notices/maintenance",
  "priority": 100,
  "active": true,
  "starts_at": "2026-11-18T00:00:00Z",
  "ends_at": "2026-11-19T00:00:00Z"
}
```

Title is required/nonblank, maximum128 UTF-16 code units. Optional body maximum4096, URL maximum2048. Body or URL must remain nonblank after normalization. Text strips surrounding whitespace; invalid controls/unpaired surrogates reject with400. Body permits line breaks/tabs. Body/title are plain text, not sanitized HTML: clients render text safely. Optional URL reuses structured post-link validation: absolute HTTP(S), no credentials/controls/malformed hosts/escapes, existing IDN limitations; no HTTP/DNS request, preview, thumbnail or reachability promise.

Priority0–1000 defaults0; active defaultsfalse. Omitted/null priority/active use those defaults. PUT replaces all content/settings; omitted nullable body/url/bounds become null and active/priority reset tofalse/0. Original creator/creation time remain; updater/update time change. Invalid replacements cannot partially overwrite content.

UTC starts_at is inclusive, ends_at exclusive; null start allows immediate eligibility when active, null end means no expiry. If both exist, end must exceed start. Offset timestamps normalize to UTC; years1000–9999 and at most microsecond precision. No scheduler is needed: reads calculate `active AND NOT deleted AND (start IS NULL OR start<=now) AND (end IS NULL OR end>now)`. Ordering: priority DESC, COALESCE(starts_at,created_at) DESC,id DESC. Future/expired windows can be stored for administration but are excluded from active reads.

Ordinary DTO: id/title/body/url/priority/starts_at/ends_at. Admin DTO additionally active/created_by/updated_by/created_at/updated_at. Author/updater come only from the authenticated numeric account, retained without user FK so account deletion does not erase provenance. Announcement rows are retained after soft deletion; audit retention does not automatically purge announcement content.

Writes lock the target row and share a transaction with ANNOUNCEMENT_CREATED/UPDATED/DELETED audit events targeting ANNOUNCEMENT ID, metadata `{}`. Audit failure rolls content changes back. Repeated deletion emits nothing; concurrent deletion serializes to one transition/event. Successful identical update commands may emit UPDATED, consistent with Phase9 taxonomy update semantics.

## Trending contract and formula

`GET /api/v1/posts/trending?window=7d&limit=10`

Supported windows: exact `24h`, `7d`, `30d` (default7d). Limit1–50 (default10); invalid choices return400. Response: `calculated_at`, `window`, `content`. Summary fields explicitly are row `id`, logical `post_id`, title, user_id, department_id, approved_at, recent_upvotes/recent_downvotes. This is a dedicated summary contract, not PostResponse: no body/files/links/entity graphs or admin-only score. Search does not gain a trending sort alias.

At one UTC calculation instant t:

1. Select the highest approved/latest/non-deleted version per logical post. Then require known approved_at within `[t-window,t)`. Defensive deduplication prevents inconsistent multiple latest flags from yielding two revisions. A newest eligible-status revision with unknown/future approval time suppresses older flagged rows rather than resurfacing historical content.
2. Count currently held UPVOTE/DOWNVOTE rows on that selected version with changed_at within the same window. Unknown/future/older vote dates do not count. Retained votes by deleted users and active posts by deleted authors remain eligible, matching existing profile-statistics policy. Historical version votes are not inherited by revisions.
3. Compute:

   `score = (1 + recent_upvotes) / ((1 + recent_downvotes) * POWER(2 + age_hours, 1.5))`

   age_hours is elapsed UTC microseconds from approved_at to t divided by3,600,000,000. Baseline1 allows zero-vote recent posts; upvotes increase rank, downvotes decrease it, age decays it. Order score DESC,approved_at DESC,id DESC. Return calculation time for reproducibility; ranks can change as time passes without new writes.

This ranks recent approvals/publication revisions, not original local-clock creation times. Legacy approvals with null dates do not qualify; they are not guessed/backfilled. Approving a new revision renews its approval age but does not transfer old votes. No view-based popularity or complete anti-abuse guarantee is claimed.

## Vote dates and compatibility

V11 adds nullable `votes.changed_at DATETIME(6)` without backfill. Existing vote routes/bodies/statuses remain unchanged: new vote or direction switch writes UTC changed_at; same-direction toggle and explicit removal delete the vote, returning existing204 semantics. Re-adding creates one currently held vote. Unique post-row/user key and existing logical-post lock prevent accumulated duplicates. Repeated switching can renew a held vote's change time; this is current-state engagement, not immutable vote-event history.

Timestamp collection runs even while trending is off, preparing truthful data for later enablement. Null legacy dates stay excluded until a genuine change/re-add. Finish upgrading every vote writer before enabling trending: old nodes can create null dates or switch a vote without updating a date. Before rolling binaries back, disable the affected homepage modules to avoid mixed-version route/data behavior.

## Queries, rollout and verification

Active announcements and trending each execute one bounded-result JDBC query, no per-row hydration/cache. Admin announcement paging uses count+page in one read-only repeatable-read transaction. Trending aggregates recent held votes, filters indexed recent approved candidates, and anti-joins higher current versions. V11 adds `idx_votes_trending_changed`, `idx_posts_trending`, plus announcement active/creation indexes. SQL bounds output/window, not total work regardless of volume; inspect production cardinalities before choosing a latency target.

Initial local MySQL8.4 corpus: 10,000 posts, 10,000 recent votes, 1,000 announcements. Trending50 rows observed41ms, active announcements50 rows2ms, one executed statement each. The all-recent corpus selected idx_status for candidates and idx_votes_trending_changed for vote aggregation; idx_posts_trending appeared among possible keys, not as the selected candidate key. `HomepageQueryPlanTest` exports actual EXPLAIN results to `target/phase-10-query-plans.json`; its current index-name assertion does not distinguish possible from selected keys (deferred test improvement). These synthetic results do not establish a production SLA.

V11 rollout:

1. Inventory production MySQL version/schema/history and back up; rehearse clean and V10→V11 migration on a restored copy. V1–V10 checksums remain unchanged. Preserve legacy post/vote/media rows and null historical dates.
2. Budget metadata locks and index-build time on posts/votes; use reviewed deployment procedures appropriate to production volume. Migration includes MySQL non-transactional DDL: repair partial failures through reviewed forward-fix/Flyway procedures, never edit applied migrations or assume destructive rollback.
3. Apply V11 before audit/homepage-aware binaries (`ddl-auto=validate`), leaving both flagsfalse. Table/index creation and timestamp collection are independent of flags.
4. Deploy all nodes, verify ordinary post/vote/auth flows, admin audit query schema, and restricted runtime grants. Runtime needs appropriate DML on announcements and updated votes/posts access; audit_events remains SELECT/INSERT only. Separate migration credentials retain DDL rights.
5. Enable each module independently when ready. Verify admin create/read/replace/delete/audit; active start/end filtering; trending only contains eligible version summaries; toggles/removals change recent counts. Disabled behavior must stay404 for authorized valid requests.
6. Observe latency, pool pressure, audit failures, dated-vote coverage and legacy approval gaps. Announcement/audit retention and recovery configuration remain separate deployment responsibilities. Disable flags as operational fallback; retain V11 schema/data on binary rollback.

Verification covers all four flag combinations, roles/service guards, CRUD/defaults/validation/Unicode, scheduling boundaries, no URL fetching, audit atomicity and concurrent delete, ranking/ties/visibility/dedup, window bounds, vote lifecycle, legacy migration preservation, representative query plans, generated OpenAPI parity and full regression suite.
