# Phase 1 Soft Deletion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement owner/admin logical-post soft deletion with safe migrations and no deleted-resource access.

**Architecture:** Retain controller → service → repository. Explicit query predicates exclude deleted rows; a shared database lock on a retained logical identity parent serializes deletion, version creation, moderation, uploads, voting, and vault additions. AUTO_INCREMENT allocates new identities atomically. Retain files and related records.

**Tech Stack:** Spring Boot 3.5.5, Java 21, JPA, MySQL 8.4, Flyway, JUnit, Testcontainers.

**Spec:** `docs/arca-backend-roadmap.md`, Phase 1 and cross-cutting delivery requirements.

## Global Constraints

- Additive schema/API changes only; preserve `/api/v1`, snake_case, JWT and roles.
- DELETE accepts a database row ID and deletes every version sharing logical `post_id`.
- Owner/admin repeat deletion returns 204 without rewriting original provenance; unrelated callers get 404 for deleted posts and 403 for forbidden active-post deletion.
- Indefinite retention initially; no physical purge, restore endpoint, or deletion fields in PostResponse.
- Existing databases require explicit reviewed baseline; never silently baseline arbitrary schemas.
- No commits or deployment against existing databases during implementation.

## Review Focus

- Concurrent deletion/version creation: no undeleted new version may survive deletion.
- Moderation after deletion: no previous approved version can resurface.
- Related records: existing vault/vote/file IDs cannot expose deleted content.
- Idempotency: repeated authorized delete preserves actor/time; unauthorized delete changes nothing.
- Migration drift: test clean bootstrap and upgrade with versioned legacy records; document production inventory requirement.

---

### Task 1: Migration foundation

**Files:** `pom.xml`, `src/main/resources/db/migration/V1__legacy_schema.sql`, `V2__post_soft_deletion.sql`, application configuration, test configuration, migration tests, CI workflow.

**Interfaces:** Produces legacy V1 schema plus V2 `posts.is_deleted`, `deleted_at`, `deleted_by` and active/latest/status index. Flyway defaults baseline-on-migrate=false; Hibernate validate.

**Review amendment:** V3 Java migration adds `post_identities`, version uniqueness, and owner consistency constraints after rejecting ambiguous legacy groups. Maven migration tooling compiles/scans Java migrations as well as SQL.

- [x] Add migration tests: clean bootstrap, explicit baseline at 1, representative legacy versions retained, default false and null provenance, repeat migrate is no-op.
- [x] Run tests and observe missing migrations fail.
- [x] Implement reference schema from current entity mappings and additive V2/V3; add MySQL-backed test datasource and CI. Production schema review remains a rollout prerequisite.
- [x] Verify migrations and Hibernate validation against MySQL 8.4.

### Task 2: Atomic deletion and access closure

**Files:** Post entity/repository/service/controller; file/vote/vault services and repositories; focused tests and MySQL integration tests.

**Interfaces:** `PostService.deletePostV1(Long rowId, Long actorId, boolean isAdmin): void`; repository logical-root locking method and bulk deletion method. Standard query methods filter `isDeleted = false`; raw `findById` remains internal for repeat deletion/provenance.

- [x] Add failing controller assertion for 204; integration tests for owner/admin/nonowner/missing/repeat/anonymous and every deleted-resource access path.
- [x] Run tests to confirm unimplemented deletion fails.
- [x] Add entity fields, explicit active query predicates, transactional logical-root lock and atomic version deletion; coordinate all related writers with same lock.
- [x] Exclude deleted resources from file metadata/download, voting/counts and vault lists/read/check/add/edit; retain stored records/files.
- [x] Test deletion versus concurrent update/moderation and transaction rollback on MySQL.
- [x] Run focused and full test suites.

### Task 3: Contract and operations

**Files:** `openapi.yaml`, `API_DOCUMENTATION.md`, controller annotations, `docs/phase-1-operations.md`.

**Interfaces:** DELETE 204, 401, 403, 404 documented with row/logical ID semantics, related access 404, retained media and baseline release procedure.

- [x] Update contract; validate YAML/OpenAPI and compare generated delete and related-resource operations.
- [x] Document schema inventory, explicit baseline, backup, migration rehearsal, mixed-version deployment constraints, observation and forward-fix steps.
- [x] Run full tests, package/check diff, and review requirements before reporting results.

## Execution ledger and rulings

- Baseline: 32 tests passed; 4 existing context tests failed because the configured database was unavailable. Disposable MySQL test configuration resolves that dependency.
- Task 1: migration tests observed missing migrations fail, then clean bootstrap/explicit-baseline upgrade and Hibernate validation passed.
- Task 2: deletion/controller/access tests observed 501, visible deleted content, and accepted deleted writes; implementation turned these green. MySQL race and rollback tests passed.
- Review: independent read-only review found an important logical-ID collision consequence and two cross-cutting delivery gaps. The original 55-test suite and JAR build passed before review fixes.
- Ruling: add retained identity parent/owner constraints in V3 — existing MAX+1 allocation allowed concurrent owners to share an ID, making logical deletion unsafe — costs one additive table/backfill and constraint reconciliation before deployment. Regression observed duplicate IDs before fix; atomic allocation and cross-owner deletion tests now pass.
- Ruling: treat related-resource OpenAPI and observability findings as required Phase 1 work — roadmap explicitly requires both — costs annotation/contract coverage plus Actuator/Micrometer and content-free correlation logging. No Phase 9 audit store is introduced.
- Ruling: keep pre-existing, non-deletion authorization gaps in Phase 2 — Phase 1 closes deleted-resource access specifically — existing pending/private-resource policy work remains required next.
- Ruling: do not cancel downloads already in flight — roadmap defines access checks and retained storage, not byte-stream revocation — a previously authorized response may finish after deletion.
- Ruling: production schema equivalence and large-table rollout remain operator prerequisites — tests use a reference legacy schema, not an inspected production snapshot — first production deployment requires inventory/reconciliation/rehearsal documented in the runbook.
- Accepted behavior remains indefinite retention, scalar deletion actor provenance, repeat authorized 204, active-nonowner 403/deleted-nonowner 404, and empty deleted history lists.
- Final review fixes: concurrent-ID and identity-integrity regressions RED→GREEN; related-resource contract drift RED→GREEN; correlation/observability regressions RED→GREEN. All three findings addressed; no review minors deferred.
- Final verification: `./mvnw --batch-mode verify -Dlogging.level.root=WARN` → BUILD SUCCESS, 62 tests, zero failures/errors/skips, executable JAR built. `git diff --check` passed. Redocly 2.78.0 minimal lint passed with 135 broader documentation warnings.
- Operational tooling check: `compile flyway:migrate flyway:validate flyway:info` using the retained MariaDB JDBC driver against disposable MySQL 8.4 applied V1–V3 and validated successfully. Temporary container removed; no existing database accessed.
