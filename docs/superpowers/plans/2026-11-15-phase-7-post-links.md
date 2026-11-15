# Structured Post Links Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ordered validated links owned by each immutable post version.

**Architecture:** V8 adds post_links with row FK, submitted display URL, canonical URL/hash, label, position and UTC timestamps. JPA cascades new links with post creation/versioning; omitted updates copy links into fresh rows, explicit empty lists clear only the new revision. Responses always include links, defaulting to [].

**Tech Stack:** Java 21 URI/IDN, Spring Boot 3.5.5, Hibernate 6, MySQL 8.4, Flyway.

**Spec:** `docs/arca-backend-roadmap.md`, Phase 7. Prior delegation (“just proceed”) continues; native execution, independent final review, commit only when requested.

## Global Constraints
- Keep V1–V7 immutable; V8 only. Parent post authorization/soft-deletion controls links; no standalone link endpoint or network fetch.
- Maximum 10 links; display URL 2048 UTF-16 code units; optional plain-text label 200; canonical ASCII URL 8192. Null collection behaves as omitted; null item invalid.
- HTTP(S) absolute URL with valid DNS/IDN, IPv4 or bracketed IPv6 host; no userinfo, whitespace/control/backslash, scoped addresses or malformed percent escapes. Ports 1–65535.
- Canonical key folds scheme/host, IDN, default ports, empty path, dot segments and unreserved percent escapes; path/query/fragment case/order remain meaningful. Duplicate keys reject whole request with 400. Display URL is trimmed submitted URL; labels strip surrounding whitespace, blank becomes null.
- New-version copies never reuse child IDs/entities. Retain existing response envelopes and plain URLs embedded in content.

## Review Focus
- Invalid update must not alter latest flags or persist a partial revision/link set.
- Omitted vs [] must retain vs clear new version without mutating historical links.
- Private/deleted parent must not leak links through direct/history/search/list routes.
- URL Unicode/authority/encoded controls must not bypass HTTP(S) validation; destination never contacted.
- Search hydration must remain constant query count with links populated, without multiple-bag fetch joins.

### Task 1: Link model, validation and version lifecycle
**Files:** New `entity/PostLink.java`, `service/links/PostLinkValidator.java`, `dto/v1/post/PostLinkRequest.java`, `PostLinkResponse.java`, V8 migration; modify Post, PostRequest/PostUpdateRequest/PostResponse, PostServiceImplementation and PostRepository.
**Interfaces:** `List<PostLink> PostLinkValidator.validate(List<PostLinkRequest>)`; response `url,label,position`; internal `PostLink.copy()` produces detached new child with same values, reset ID/timestamps.
- [x] Add failing MVC tests for create/read/version copy/replace/clear/history, legacy [], invalid links, authorization and rollback. Add normalization unit cases before validator implementation.
- [x] Implement validated owned children, cascade persistence and response mapping; validate before persistent writes, after parent authorization on updates.
- [x] Add separate search-page link fetch and prove constant fetch count with representative populated links; run focused tests GREEN.

### Task 2: Migration and wire contracts
**Files:** PostMigrationTest, OpenApiContractTest, openapi.yaml; docs/phase-7-operations.md, README.md, API_DOCUMENTATION.md.
- [x] Test V7→V8 preserving existing post/media data, clean bootstrap/idempotence, per-row duplicate and ordering constraints, parent FK retention/cascade.
- [x] Export generated API, synchronize schemas and parity checks; document limits, canonicalization/duplicates, null/omitted/empty semantics, display safety and V8 rollout.

### Task 3: Release verification
- [x] Full Maven verify with default observation logging; OpenAPI parity, pinned Redocly lint and diff whitespace check.
- [x] Packaged-JAR smoke with disposable MySQL/production MariaDB driver: JWT create/approve/update/history/invalid links and deletion visibility.
- [x] Independent read-only whole-branch review; fix important findings with regression evidence and record results.

## Ledger
- Clean develop at `61427e3` includes merged Phase 6; created `feat/post-links`.
- Hibernate docs confirm separate collection fetches avoid multiple-bag/cartesian fetches.
- Pre-flight: Task 1 DTO/entity mapping consumed by Task 2 schemas; names/limits above fixed. Task 3 uses same wire shape, no conflicting interfaces.
- Initial RED: created post returned zero links instead of two; focused lifecycle/validator/service GREEN after implementation (41 tests). Initial schema validation exposed CHAR/VARCHAR hash mapping mismatch; explicit CHAR(64) entity definition aligns V8.
- Search corpus now includes 10,000 stored links; observed four JPA statements at both sizes 1 and 100, plus existing two JDBC page/count queries. Query budget updated for the one deliberate collection fetch.
- Migration check-constraint test initially expected DataIntegrityViolationException, but MySQL 3819/HY000 is mapped to UncategorizedSQLException. Assertion now checks DataAccessException plus exact driver error code 3819; schema behavior was correct.
- Independent review identified four important defects: Java URI normalization collapsed repeated slashes; ASCII conversion applied Unicode NFC; legacy IDN merged browser-distinct hostnames; OpenAPI lacked explicit null unions. Regression run confirmed failures, followed by fixes and focused GREEN (44 tests).
- Final: fixed slash/Unicode normalization — `repeatedSlashesAndUnicodeSpellingsRemainDistinctResources` RED→GREEN; RFC 3986 dot removal preserves empty segments and UTF-8 encoding preserves original Unicode code points.
- Ruling: reject Unicode IDNA deviation characters (sharp S, final sigma, ZWNJ/ZWJ) rather than add a full browser-IDNA dependency — avoids silently merging unrelated destinations while keeping implementation bounded — cost: these Unicode host spellings require intended ASCII/punycode input. Regression `idnaDeviationCharactersRejectRatherThanSilentlyMergingBrowserDestinations` RED→GREEN.
- Final: fixed null schema contracts — explicit OpenAPI 3.1 null unions on labels/request collections; contract assertions RED→GREEN.
- Task 1: complete; Task 2: complete; Task 3: complete. Final `./mvnw --batch-mode verify -Dlogging.level.root=WARN -DtrimStackTrace=true`: **270 tests, zero failures/errors/skips**, executable JAR built. OpenAPI parity included; export inspected for schema sync. Redocly 2.78.0 minimal lint valid with same **135 baseline warnings**; `git diff --check` clean.
- Packaged JAR + production MariaDB JDBC driver + disposable MySQL 8.4 smoke passed V8 startup, real JWT visibility, ordered display links, invalid/duplicate atomicity, copy/clear/history and soft-deletion retention; temporary app/container/storage removed.
- All four review findings addressed in one fix pass with full green suite; no deferred findings. Phase 7 remains uncommitted pending user request.
