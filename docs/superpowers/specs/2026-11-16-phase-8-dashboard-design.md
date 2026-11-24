# Phase 8 — Admin dashboard

Approved direction: dedicated nullable `posts.approved_at`; never infer legacy approval dates.

## Intent and scope
Expose `GET /api/v1/admin/dashboard` for ROLE_ADMIN, retaining existing API contracts. Return users, posts, moderation and engagement aggregates, effective UTC window and calculation timestamp. No PII, entity loading, cache or audit infrastructure.

## Approaches
1. Current-state approximation using updated_at: smaller, but cannot answer approval activity honestly.
2. Dedicated approved_at: selected by user; records first successful approval of each version from deployment onward, retained after superseding/deletion.
3. Approval event log: richer action history, deferred to Phase 9.

## Reporting decisions
- Optional ISO offset date-time `from` inclusive / `to` exclusive; normalize to UTC. Default to=now, from=to minus 90 days; from alone uses now as to. Require positive range <=90 elapsed days, years 1000–9999; future windows allowed.
- Current users: false/null is_deleted means active; roles count distinct active users per non-null role, sorted by role. Multiple roles intentionally overlap.
- Current logical posts: highest version per logical ID (including rejected submissions whose latest flag is false), exclude deleted logical posts. Group by current status; report deleted logical count separately. Pending count uses same current-status definition.
- Period activity: logical creations use version=1 created_at; approvals count version rows with approved_at in window, including superseded/deleted rows. Legacy approved/superseded rows with null approved_at reported separately as unknown-date count, never attributed to window.
- Engagement: files/votes/vault saves on approved/latest/non-deleted post rows, consistent with profile statistics. Retained activity by deleted users remains counted. File bytes sum known nonnegative metadata only; unknown byte sizes counted separately; not physical disk usage.
- All numbers int64. Seven parameterized aggregate queries in one repeatable-read, read-only transaction; no cache. Timestamp describes calculation start, not event-history completeness.
- V9 adds nullable DATETIME(6) only. Approval writes UTC explicitly; existing legacy created_at clocks cannot be repaired retrospectively and remain a documented limitation.

## Delivery
MySQL integration fixtures cover role overlap, versions/statuses/deletion, empty results, offset/boundary validation, file sizes above int32, authorization and approval lifecycle/rollback. V8→V9 and clean migration tests; generated OpenAPI parity; operational rollout and EXPLAIN evidence. Existing migrations immutable. No commit unless requested.
