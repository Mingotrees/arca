# Phase 3 — Post search, filters, sorting and pagination

## API and compatibility

`GET /api/v1/posts/search` requires the existing bearer JWT. It returns:

```json
{"content":[],"page":0,"size":20,"total_elements":0,"total_pages":0,"sort":"newest"}
```

`content` contains existing `PostResponse` payloads, including subjects, attachments and vote counts. IDs in content identify version rows; `post_id` remains the logical identity. Legacy post/subject/department/author lists retain their unpaged arrays and existing query semantics.

| Parameter | Meaning |
| --- | --- |
| `q` | Natural-language title/content query, maximum 200 characters; trimmed and blank treated as absent |
| `subject_id`, `department_id`, `author_id` | Positive IDs; all supplied filters intersect; unknown positive IDs produce an empty page |
| `from`, `to` | Inclusive lower and exclusive upper bound on `created_at`, respectively |
| `page` | Zero-based integer, default 0; negative or overflowing integer rejected |
| `size` | Integer 1–100, default 20 |
| `sort` | `newest`, `oldest`, `relevance`, `vote_score` |
| `status` | Explicit filter is admin-only, even when requesting APPROVED |

Dates are ISO **local date-times**, for example `2026-01-01T00:00:00.123456`, within MySQL DATETIME years 1000–9999 and at most microsecond precision. Offsets/`Z`, sub-microsecond values, invalid dates and `from >= to` are rejected. Existing timestamps have no stored zone: filter against the same wall-clock values returned as `created_at`; this release does not reinterpret historical data as UTC. Either bound may be omitted.

Default sorting is `relevance` with a nonblank query and `newest` otherwise. Relevance without a query is invalid. Newest/oldest use creation time descending/ascending and row ID in the same direction. Relevance uses MySQL score descending then row ID descending. Vote score means upvotes minus downvotes on the version row, descending then row ID descending; it is not trending.

Default search always requires APPROVED, `is_latest_version=true`, non-deleted rows, including for owners/admins. Admins can explicitly request `APPROVED`, `PENDING_APPROVAL`, `REJECTED` or `SUPERSEDED` (case normalized). Explicit APPROVED uses the default eligibility rule. Other statuses select the newest stored active version of each logical post: rejected revisions remain discoverable even though rejection clears their latest flag. Earlier matching moderation versions are not returned when a newer stored version exists. Existing moderation queues/history routes remain available.

Invalid arguments return the standard JSON 400 envelope; absent authentication returns 401; valid explicit status filtering by nonadmins returns 403. Empty and beyond-last pages retain real totals; zero matching rows means `total_pages=0`. Large page offsets are computed as longs and an offset beyond the count skips the row query. Page size bounds response assembly, but count/full-text/vote sorting still process the matching set. Offset pagination can shift across separate requests when posts/votes change; stable order is guaranteed for unchanged data and ranking statistics, not a cross-request snapshot.

Example:

```sh
curl --get http://localhost:20255/api/v1/posts/search \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  --data-urlencode 'q=quantum mechanics' \
  --data-urlencode 'department_id=1' \
  --data-urlencode 'from=2026-01-01T00:00:00' \
  --data-urlencode 'to=2026-02-01T00:00:00' \
  --data-urlencode 'page=0' --data-urlencode 'size=20'
```

## Full-text semantics

V4 applies `utf8mb4_0900_ai_ci` to title/content and adds `ft_posts_title_content` using MySQL's built-in InnoDB parser. Matching is case/accent insensitive (for example CAFE matches café). Natural-language tokens are not substring or wildcard expressions; `%`, `_`, quotes and Boolean-looking operators are bound data, never SQL. Defaults exclude stopwords and tokens shorter than 3 or longer than 84 characters; a query consisting only of excluded tokens produces an empty result. Multiword natural-language search follows MySQL relevance semantics, not a promise that every query word occurs. Languages needing ngram segmentation require a separate parser decision.

Ranking depends on the whole indexed corpus and index statistics, including non-result versions. Refresh optimizer statistics after bulk imports/rehearsals with `ANALYZE TABLE posts,post_tags,votes`; do not run ANALYZE per HTTP request. Tiny fixture replacement initially produced zero-score ties until statistics were refreshed; row-ID tie-breaking still kept those results deterministic. Scores are not persisted, cached or returned as an additional client field.

## V4 rollout

V1–V3 remain unchanged. V4 changes only searched text-column collation/character set and adds full-text/B-tree indexes; post IDs, rows, versions, ownership, deletion provenance, files, votes and vaults remain intact. This release targets **MySQL 8.4**, not a MariaDB server; the existing MariaDB JDBC driver remains supported.

Before migration:

1. Inventory production MySQL version, table engine, title/content types/collations, index names, row count, available disk and active long transactions. Validate the retained Phase 1 baseline and back up the database.
2. Inspect full-text settings (`SHOW VARIABLES LIKE 'innodb_ft_%'`), default/server/user stopword tables, and confirm built-in parser behavior. V4 documents default min/max token lengths 3/84 and enabled default stopwords; custom settings need an explicitly reviewed search contract and index rebuild before release.
3. Verify character-set expansion fits retained field types. In particular, inspect rows where `OCTET_LENGTH(CONVERT(content USING utf8mb4)) > 65535`; do not silently truncate a legacy single-byte TEXT value whose UTF-8 form exceeds TEXT capacity. Reconcile such data through a separately reviewed widening migration before applying V4.
4. Rehearse V3→V4 on a production-sized restored copy. First InnoDB FULLTEXT creation may rebuild the table to add hidden `FTS_DOC_ID`; tests observed MySQL's rebuild warning. Do not assume online/nonblocking DDL or transaction rollback for a multi-statement migration. Schedule a write-maintenance window, budget temporary disk and measure actual duration.
5. Deploy V4 before enabling clients to use the search endpoint. Old application code can continue using old indexes/columns, but an old migrator configuration must not attempt to alter/repair V4 history. Keep index creation out of Hibernate schema mutation.

After migration, verify Flyway V4 success, text preservation/collations and index definitions; refresh statistics; run representative EXPLAIN checks and authenticated search smoke tests. Retain the backup. A migration interrupted between the posts and post_tags ALTER statements needs operator inspection and a reviewed Flyway repair/forward fix after schema reconciliation; do not rerun ALTERs blindly or edit an applied V4.

## Observed synthetic query plans

`PostSearchQueryPlanTest` loads 10,000 posts, 100 authors, 10 departments, 100 subjects, 20,000 subject links, 1,000 votes and 200 files into disposable MySQL 8.4. Results below are observed test plans, **not production latency or capacity claims**. `target/phase-3-query-plans.json` contains raw page/count JSON EXPLAIN output after the test.

| Query | Observed access |
| --- | --- |
| Newest approved/latest | `posts` range/ref scan using `idx_posts_search_created` |
| Department + newest | `posts` ref lookup using `idx_posts_search_department` |
| Author + newest | `posts` ref lookup using `idx_posts_search_author` |
| Subject | `post_tags` ref lookup on `idx_post_tags_subject_post`, then posts PRIMARY eq_ref |
| Rare full-text term | `posts` fulltext access on `ft_posts_title_content` |
| Vote score | Matching posts via `idx_status` or `idx_posts_active_latest_status` across observed runs; dependent vote lookup via `uk_votes_post_user`, then score sort |

Count and page-ID statements reuse the same bound predicates; subject filtering uses EXISTS rather than a multiplying join. The service uses one read-only repeatable-read transaction for JDBC selection and JPA assembly. Ranked IDs are reassembled after batched entity fetches; existing eager role/subject-department relationships are fetched deliberately, with files separately to avoid multiple-bag fetches. Vote counts use one grouped query. Measured mapping uses three JPA statements at page size 1 and 100, plus two JDBC count/page statements: five total for nonempty pages. Association join fan-out still follows actual subjects/department relationships; page size does not impose a new cap on existing per-post metadata.

Vote sorting and exact counts are costlier than a simple newest page on very large datasets. Capture plans on production-like selectivity, watch latency and use the bounded page-size contract; do not introduce cached counters or trending semantics without measurement and a separate design.

## Observability and verification

Search requests return a sanitized/generated `X-Request-ID`; `arca.post.search` records status/outcome and duration, including pre-security 401s and validation/authorization rejections. Logs contain the event, status, outcome, safe correlation ID and duration only, never raw q, request bodies, headers, credentials or post content. Existing `post_delete` logs and `arca.post.deletion` names remain supported. Admin-only Actuator metrics retain their existing access controls; default meters are process-local.

Run `./mvnw --batch-mode verify` and the pinned README OpenAPI lint command. Focused checks cover clean/legacy migrations, visibility/status/role matrices, intersection filters, Unicode/escaped/short queries, date boundaries, invalid page/sort/IDs, stable ties, empty/last pages, complete PostResponse payloads, unchanged legacy arrays, content-free observations, generated contract parity and indexed execution at representative synthetic volume.
