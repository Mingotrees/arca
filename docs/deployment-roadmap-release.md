# Roadmap release — deployment preparation

## Observed repository/production state (2026-11-17)

- Phase10 is implemented and verified on `feat/optional-announcements-and-trending`, based on merged Phase9 `c968a98`; the production release remains pending.
- `.github/workflows/deploy-backend.yml` deploys to DCISM on pushes to **master** or manual dispatch. It builds/verifies, uploads the JAR over SSH, restarts the process, and checks `/v3/api-docs` on localhost:20255.
- Required repository secret names exist: DCISM_SSH_KEY, DCISM_KNOWN_HOSTS, DCISM_HOST, DCISM_PORT, DCISM_USER, DCISM_DEPLOY_PATH. Their values were not retrieved or displayed.
- Latest observed successful deployment: [run43554738112](https://github.com/Mingotrees/arca/actions/runs/43554738112), 2026-11-14, master `d1ed396` (PR29).
- GitHub reports develop16 commits ahead/1 behind master. Deployment must account for the complete release delta, not just Phase10.
- Read-only public production OpenAPI at `https://arca-backend.dcism.org/v3/api-docs` currently does not advertise post search, recovery, cover images, dashboard, audit or trending. Subsequent SSH-tunneled preflight and the approved backup/rehearsal below establish the current database and restore evidence.

## Confirmed SSH/database preflight

- SSH target: `s22101440@ver.dcism.org`, port22072, local identity `~/.ssh/dcism_server`. Public-key authentication was verified without an account-password prompt.
- Running backend directory: `/data/users/s22101440/arca-backend`, JAR `arca.jar`; server-side `.env` exists.
- Database reached through an explicitly loopback-bound SSH local forward to server `127.0.0.1:3306`. Existing backend credentials stayed in process memory; no credential file was copied into the repository. Inspection transaction was explicitly READ ONLY and rolled back; temporary tunnels closed afterward.
- Product/version: **MariaDB11.8.6**, database `s22101440_arca`, utf8mb4/utf8mb4_general_ci. The implementation regression suite primarily targets MySQL8.4; MariaDB-specific upgrade/runtime rehearsal is required before live changes.
- Flyway history: baseline1, successful V2 post soft deletion (checksum168964646), successful V3 logical post identity. Pending release migrations: **V4–V11**. No new baseline or migration was attempted.
- V4 collation name `utf8mb4_0900_ai_ci` is recognized by this server. MariaDB documents this as a UCA1400 alias; recognition does not establish exact MySQL8 search semantics. Current InnoDB full-text token limits are3/84.
- Post version/identity/vote uniqueness and parent foreign keys exist. Read-only aggregate integrity check found1 active post, no missing active author/department references.
- Runtime database account has schema-wide ALL PRIVILEGES. Separate restricted runtime grants and migration/operator credentials are not established by this inspection; an appropriately authorized database administrator is needed to implement the reviewed privilege split.
- Database time_zone=SYSTEM and system_time_zone label=PST. Do not infer a UTC offset from the label alone or rewrite historical local-clock timestamps without a reviewed derivation.
- Preflight was read-only. The subsequently approved maintenance window captured a matched backup and restarted the same backend; the isolated rehearsal below has now passed. Live V4–V11 deployment and privilege changes remain pending.

## Verified backup and rehearsal (2026-11-19)

- User explicitly approved the short backend pause. The existing process was gracefully stopped; database, media, current JAR and `.env` were captured together. The same executable/arguments/environment restarted the existing artifact, preserving configuration and signing secret.
- Pause plus restart: **10.83 seconds**. Backend resumed as PID2544989; localhost and public `/v3/api-docs` returned200. A closing read-only check confirmed production still at **Flyway V3**, with its one media reference/object intact.
- Server backup: `/data/users/s22101440/arca-backups/20261119T045514Z-q535omqi`.
- Protected off-server copy: `/home/abliss/.local/share/arca-backups/20261119T045514Z-q535omqi`.
- Backup directories are0700; database.sql, runtime-media.tar.gz and manifest.json are0600. SQL/archive SHA-256 checksums match across SSH transfer. No backup content or credentials belong in Git; these directories are outside the repository. Protection here is filesystem permissions plus encrypted SSH transfer, not a claim of at-rest encryption.
- Runtime archive JAR/configuration hashes were verified without extracting configuration secrets into the test app. Restored media references resolved to the captured object and matched recorded size. All16 source table row counts matched the paused inventory.
- Restored into disposable **MariaDB11.8.6** (`mariadb:11.8.6`, Ubuntu image; production uses Debian packaging). Applied **V4–V11** successfully without changing applied migrations or creating a new baseline.
- Fingerprints of original projected columns/rows matched before and after migration/startup. Original Flyway rows1–3 were preserved; complete clone history reachedV11. Expected new/backfilled columns are outside the legacy projection comparison.
- Upgraded app ran using the production MariaDB JDBC driver and Java21. Verified real JWT roles; dashboard; audit authorization/redaction; full-text search; approval/deletion; timestamped voting/trending; profile metadata/statistics; announcements/audit; disabled-recovery behavior; and download bytes of the actual backed-up media object.
- In the **clone only**, provisioned separate migrator/runtime users. App restart succeeded with migration credentials separated from request-serving credentials; audit-table UPDATE and DELETE were denied to runtime while normal audit inserts/queries worked.
- Verification receipts: manifest.json and rehearsal-report.json in both backup locations. Detailed diagnostic/snapshot records stay private in the local backup directory. Disposable database/app containers and their volumes were removed after verification.
- Remaining release work: commit/review Phase10, provision the production privilege split with an authorized database administrator, approve the live migration/deployment window, and configure retention/recovery as applicable. Production migrations/grants were not changed by rehearsal.

## Next access needed

SSH/database read access and backup/restore rehearsal are now established. Administrator access for provisioning the production runtime/migration/operator split remains to be resolved. GitHub secret presence does not expose its deployment key to this local session; the separately approved local key was used. Do not paste private keys/passwords into chat. A manual dispatch of the existing deployment workflow is a real deployment, not a read-only preflight.

Release follow-up on2026-11-19: full Maven verification again passed306 tests with zero failures/errors/skips. A fresh SSH privilege inspection timed out connecting to port22072 before authentication, so no production account change was attempted. The [database-account request](dcism-database-account-request.md) specifies the administrator work and post-V11 grant order.

## Read-only preflight

Record the exact target host/service/deploy directory and running revision, Java21 path, runtime profile, file.upload-dir/persistent-media location, and whether multiple writers exist. Inspect configuration **names/presence only** in shared reports; keep actual environment/credentials on the server.

Database operator should capture these non-content inventory queries through an approved client:

```sql
SELECT VERSION() AS database_version, DATABASE() AS schema_name,
       @@character_set_database AS character_set, @@collation_database AS collation;

SELECT table_name, engine, table_collation, table_rows, data_length, index_length
FROM information_schema.tables
WHERE table_schema = DATABASE()
ORDER BY table_name;

SELECT table_name, column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
ORDER BY table_name, ordinal_position;

SELECT table_name, index_name, non_unique, seq_in_index, column_name, index_type
FROM information_schema.statistics
WHERE table_schema = DATABASE()
ORDER BY table_name, index_name, seq_in_index;

SELECT COUNT(*) AS has_flyway_history
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history';
```

Only if history exists, inspect installed_rank/version/description/type/checksum/success from flyway_schema_history. Compare history and SHOW CREATE TABLE output to the release migrations. Never assign a baseline to an unknown populated schema or run V1 over it. Inventory ambiguous logical-post identities and production table sizes using [Phase1 operations](phase-1-operations.md) before any pending identity/index migration.

Inspect runtime/migration/operator grants privately with the database administrator. Grants are additive: audit-table SELECT/INSERT-only enforcement requires removal of broader runtime UPDATE/DELETE/DDL grants. Verify actual server/dialect support rather than inferring product from the client driver; this target is MariaDB11.8.6 and the restored-backup rehearsal above passed.

## Release sequence after preflight

1. Commit/review Phase10, merge the approved feature into develop, and prepare a release diff against master. Complete live inventory, verified database/media backup and restored-snapshot migration rehearsal **before merging master**, because that push triggers deployment automatically.
2. Reconcile schema drift with a reviewed environment-specific plan. Compile Java migrations and use separately provisioned Flyway credentials; inspect info/validate before deciding any explicit baseline. Preserve the existing JWT secret so issued tokens remain compatible.
3. Drain traffic/stop all old writers where required by the reviewed migration plan. Apply pending migrations through V11, validate schema/history, and verify legacy data/media. Budget collation/full-text/index rebuilds and metadata locks; do not assume the first deployment can safely migrate every pending phase during unattended startup.
4. Configure separate runtime/migration credentials and audit-table grants; provision operator-managed365-day audit retention/export lifecycle. Preserve persistent uploads/avatars/covers.
5. Deploy the verified committed artifact using the established DCISM path. Keep ANNOUNCEMENTS_ENABLED=false and TRENDING_ENABLED=false until every writer is upgraded. Check authenticated legacy routes, moderation/deletion, search, profiles, audit/admin authorization, and media access in addition to public OpenAPI health.
6. Enable homepage flags independently and verify the [Phase10 contract](phase-10-operations.md). Observe latency/errors, lock/pool pressure, audit failures and vote-date coverage before setting a production SLA.
7. Recovery reminder: **apply V5 token/rate tables** through the reviewed rollout. Recovery stays disabled until SMTP, frontend reset URL, sender identity and a separate rate-key secret are configured and a real delivery/reset check passes. Committed backend code does not prove production recovery is enabled.

The approved backup maintenance restarted the existing production backend. No live migration, master merge, workflow dispatch, secret change, grant change or retention purge was performed during this preparation/rehearsal.
