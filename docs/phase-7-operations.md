# Phase 7 — Structured post links

## Wire contract

Existing post creation and version-update routes accept optional `links`:

```json
{
  "title": "Reference notes",
  "content": "<p>See the <strong>references</strong> below.</p>",
  "department_id": 1,
  "links": [
    {"url": "https://example.org/reference", "label": "Reference"},
    {"url": "https://example.org/appendix"}
  ]
}
```

Existing create/update success envelopes remain unchanged. Read/list/search/history `PostResponse` adds `links`, always an ordered array, including `[]` for old posts or no links. Each item contains `url`, optional/null `label`, and zero-based `position`. Internal canonical keys, child IDs and persistence timestamps are not returned. Inline URLs are not converted to structured links. See the [post body contract](post-content.md) for HTML handling.

Creation with omitted/null links stores none. Updating an approved version with omitted/null links **copies the addressed source row's links into new child rows** in the new pending version. Explicit `[]` clears links only in that new revision; a nonempty array replaces the full list. Child IDs/entities are never shared between versions. Approval/rejection retains the submitted version's set; a historical read reports that row's original set. The existing logical-post lock serializes version mutation and deletion. Link validation happens before any persistent creation/version/latest-flag change; writes are atomic with the post transaction.

## Validation and duplicate identity

- Maximum **10** links, submitted URL **2,048 UTF-16 code units**, label **200 UTF-16 code units**, canonical ASCII URL **8,192 characters**. Limits include submitted surrounding whitespace before trimming.
- URL is required; null items, missing/blank URLs, malformed URI/Unicode/host, unsupported scheme, credentials/userinfo, literal whitespace/control/backslash, encoded ASCII controls/backslashes, scoped IPv6, and invalid ports reject the entire request with JSON **400**. No partial posts/links or changed latest flags survive invalid requests.
- Only absolute `http` / `https` with DNS/IDN, IPv4 or bracketed IPv6 host. Ports 1–65535; default 80/443 suppressed in the canonical key. Local/private destinations can be stored because the backend does not fetch them. No promise of public reachability or destination safety is made.
- Display URL preserves submitted spelling after surrounding whitespace is stripped. Canonicalization folds scheme/host case, converts supported IDN hosts to ASCII with Java's IDN rules, strips trailing host dot, supplies `/` for empty path, removes RFC 3986 dot segments while preserving repeated slashes, normalizes percent escapes, and decodes unreserved ASCII escapes. Non-ASCII path/query/fragment code points are UTF-8 percent-encoded without Unicode normalization: composed/decomposed spellings remain distinct resources. Query order and path/query/fragment case remain significant; fragments participate in duplicate identity. Equivalent IPv6 compressed spellings are not expanded to one form.
- Java's IDN implementation is IDNA2003. Unicode hostnames containing sharp S (`ß`/`ẞ`), final sigma (`ς`), ZWNJ or ZWJ reject with 400 rather than silently mapping to a different modern-browser destination. Use the intended ASCII/punycode hostname for these names. This deliberately limited IDN policy avoids adding another normalization dependency; it does not promise full UTS #46 support.
- Canonical duplicates within a submitted version return **400**, rather than silently dropping/reordering input. Different versions may store the same link. SHA-256 canonical hash plus post-row ID forms the database uniqueness key; collision would reject distinct input rather than overwrite existing data.
- Labels strip surrounding whitespace; blank becomes null. Labels are plain text, not sanitized HTML; controls and unpaired surrogates reject. Clients must render labels as text and set URL attributes safely. No server-side previews, title/thumbnail lookup, DNS lookup or HTTP requests occur when submitting/reading links.

Authorization and visibility are inherited from the parent post. Pending/rejected/superseded links are visible only where the corresponding version is readable by the owner/admin. Other callers get existing hidden-parent behavior. Logical soft deletion retains child rows for history/provenance but hides them with all versions through direct/list/search/history endpoints. There is no independently addressable link endpoint.

## Storage and query behavior

V8 adds `post_links`: generated ID, `post_id` FK to **posts.id (version row)**, display/canonical URL, binary-collated ASCII SHA-256 hash, label, position and UTC DATETIME(6) creation/modification timestamps. Unique constraints protect `(post_id,normalized_hash)` and `(post_id,position)`; check constraint bounds position to 0–9. Rows are immutable within an application-created version; `updated_at=created_at` until an explicitly reviewed future maintenance operation. Physical post deletion cascades link deletion; ordinary logical soft deletion retains them.

Search loads links using one separate page-bounded collection fetch, avoiding parallel bag fetch joins. Representative 10,000-post corpus with populated links observes four JPA statements at both page size 1 and 100, plus the existing two JDBC count/page queries. Existing unpaged list routes retain their historical query behavior; this phase does not redesign their pagination or graph loading.

## V8 rollout and verification

V1–V7 stay unchanged. V8 creates an empty child table without backfilling content or changing existing posts, files, sessions or cover data. Rehearse V7→V8 on a restored copy, inspect table/index-name conflicts, take a backup and budget the brief parent metadata/FK lock. Apply V8 before deploying link-aware binaries (`ddl-auto=validate`). Old binaries tolerate the extra table, but updates from old nodes do not copy links; finish the rollout before accepting link-bearing version edits. Binary rollback retains link rows; avoid destructive down-migrations. Reconcile partial DDL using reviewed Flyway procedures rather than editing applied files.

Verification covers clean/baseline/V7 upgrades and constraints; order and display round-trip; copied/replaced/cleared immutable versions; legacy and null input; URL/Unicode/size/duplicate rejection; atomic invalid/outer rollback; parent visibility/deletion; no destination fetch; constant search query count; OpenAPI parity, lint, full Maven verify, packaged JAR/production JDBC driver with disposable MySQL smoke.
