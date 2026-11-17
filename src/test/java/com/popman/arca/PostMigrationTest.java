package com.popman.arca;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;

import static org.junit.jupiter.api.Assertions.*;

class PostMigrationTest {
    @Test
    void bootstrapsCleanDatabaseAndMigratesOnlyOnce() {
        try (MySQLContainer<?> db = new MySQLContainer<>("mysql:8.4")) {
            db.start();
            Flyway flyway = flyway(db);
            assertEquals(10, flyway.migrate().migrationsExecuted);
            assertEquals(0, flyway.migrate().migrationsExecuted);
            JdbcTemplate jdbc = jdbc(db);
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = 'posts' "
                    + "AND column_name IN ('is_deleted','deleted_at','deleted_by')", Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() "
                    + "AND table_name='posts' AND index_name='ft_posts_title_content' AND index_type='FULLTEXT'", Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
                    + "AND table_name='posts' AND column_name IN ('title','content') AND collation_name='utf8mb4_0900_ai_ci'", Integer.class));
        }
    }

    @Test
    void upgradesExplicitlyBaselinedLegacyVersionsWithoutLosingData() {
        try (MySQLContainer<?> db = new MySQLContainer<>("mysql:8.4")) {
            db.start();
            Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                    .target("1").load().migrate();
            JdbcTemplate jdbc = jdbc(db);
            jdbc.update("INSERT INTO users (id,email) VALUES (1,'legacy@example.test')");
            jdbc.update("INSERT INTO school (id,name) VALUES (1,'School')");
            jdbc.update("INSERT INTO departments (id,name,school_id) VALUES (1,'Department',1)");
            jdbc.update("INSERT INTO posts (post_id,version,title,content,status,user_id,department_id,"
                    + "is_latest_version,created_at,updated_at) VALUES "
                    + "(7,1,'Old','Body','APPROVED',1,1,false,NOW(),NOW()),"
                    + "(7,2,'New','Body','PENDING_APPROVAL',1,1,true,NOW(),NOW())");
            // Simulate an existing, unversioned deployment after creating its legacy schema.
            jdbc.execute("DROP TABLE flyway_schema_history");
            assertThrows(Exception.class, () -> flyway(db).migrate());
            Flyway baseline = flyway(db);
            baseline.baseline();
            assertEquals(9, baseline.migrate().migrationsExecuted);
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM posts WHERE post_id=7 "
                    + "AND is_deleted=false AND deleted_at IS NULL AND deleted_by IS NULL", Integer.class));
            assertEquals(1L, jdbc.queryForObject("SELECT owner_id FROM post_identities WHERE id=7", Long.class));
            assertEquals("Old", jdbc.queryForObject("SELECT title FROM posts WHERE post_id=7 AND version=1", String.class));
            assertEquals("Body", jdbc.queryForObject("SELECT content FROM posts WHERE post_id=7 AND version=2", String.class));
            assertEquals(0, baseline.migrate().migrationsExecuted);
        }
    }

    private Flyway flyway(MySQLContainer<?> db) {
        return Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .baselineVersion("1").baselineOnMigrate(false).load();
    }

    @Test
    void addsSearchIndexesToV3WithoutChangingUnicodeRowsOrDeletionProvenance() {
        try (MySQLContainer<?> db = new MySQLContainer<>("mysql:8.4")) {
            db.start();
            Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).target("3").load().migrate();
            JdbcTemplate jdbc = jdbc(db);
            jdbc.update("INSERT INTO users(id,email) VALUES(1,'retained@example.test')");
            jdbc.update("INSERT INTO school(id,name) VALUES(1,'School')");
            jdbc.update("INSERT INTO departments(id,name,school_id) VALUES(1,'Department',1)");
            jdbc.update("INSERT INTO post_identities(id,owner_id) VALUES(7,1)");
            jdbc.update("INSERT INTO posts(id,post_id,version,title,content,status,user_id,department_id,is_latest_version,"
                    + "is_deleted,deleted_at,deleted_by,created_at,updated_at) VALUES "
                    + "(10,7,1,'Résumé','café electron αβγ','APPROVED',1,1,true,true,'2026-01-02',1,'2026-01-01','2026-01-01')");
            jdbc.update("INSERT INTO files(post_id,user_id,file_name) VALUES(10,1,'retained.pdf')");
            jdbc.update("INSERT INTO votes(post_id,user_id,vote_type) VALUES(10,1,'UPVOTE')");
            var before = jdbc.queryForMap("SELECT * FROM posts WHERE id=10");
            Flyway searchMigration = Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).target("4").load();
            assertEquals(1, searchMigration.migrate().migrationsExecuted);
            assertEquals(before, jdbc.queryForMap("SELECT * FROM posts WHERE id=10"));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM files WHERE post_id=10", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM votes WHERE post_id=10", Integer.class));
            assertEquals(0, searchMigration.migrate().migrationsExecuted);
        }
    }

    @Test
    void upgradesV4WithHashOnlyRecoveryTablesWithoutRevokingExistingSessions() {
        try (MySQLContainer<?> db = new MySQLContainer<>("mysql:8.4")) {
            db.start();
            Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).target("4").load().migrate();
            JdbcTemplate jdbc = jdbc(db);
            jdbc.update("INSERT INTO users(id,email,password,is_deleted) VALUES(1,'retained@example.test','retained-bcrypt',false)");
            jdbc.update("INSERT INTO refresh_tokens(user_id,token,expiry_date) VALUES(1,'retained-session','2027-01-01')");
            var before = jdbc.queryForMap("SELECT * FROM refresh_tokens");
            Flyway recoveryMigration = Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).target("5").load();
            assertEquals(1, recoveryMigration.migrate().migrationsExecuted);
            assertEquals(before, jdbc.queryForMap("SELECT * FROM refresh_tokens"));
            assertEquals("retained-bcrypt", jdbc.queryForObject("SELECT password FROM users WHERE id=1", String.class));
            jdbc.update("INSERT INTO password_reset_tokens(user_id,token_hash,created_at,expires_at) VALUES(1,?,UTC_TIMESTAMP(6),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 15 MINUTE))", "a".repeat(64));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                    "INSERT INTO password_reset_tokens(user_id,token_hash,created_at,expires_at) VALUES(1,?,UTC_TIMESTAMP(6),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 15 MINUTE))", "a".repeat(64)));
            jdbc.update("DELETE FROM refresh_tokens"); jdbc.update("DELETE FROM users WHERE id=1");
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens", Integer.class));
            assertEquals(0, recoveryMigration.migrate().migrationsExecuted);
        }
    }

    @Test
    void backfillsV5ProfileMetadataAsApproximateAndPreservesExistingData() {
        try (MySQLContainer<?> db = new MySQLContainer<>("mysql:8.4")) {
            db.start();
            Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).target("5").load().migrate();
            JdbcTemplate jdbc = jdbc(db);
            jdbc.update("INSERT INTO users(id,email,password,is_deleted) VALUES(1,'old@example.test','retained-hash',false),(2,'deleted@example.test','retained-hash',true)");
            jdbc.update("INSERT INTO subjects(id,code,name) VALUES(1,'OLD','Legacy subject')");
            jdbc.update("INSERT INTO refresh_tokens(user_id,token,expiry_date) VALUES(1,'retained-session','2027-01-01')");
            var session = jdbc.queryForMap("SELECT * FROM refresh_tokens");
            java.time.LocalDateTime before = jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", java.time.LocalDateTime.class);
            assertEquals(1, Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).target("6").load().migrate().migrationsExecuted);
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE created_at IS NOT NULL AND updated_at=created_at AND timestamps_approximate=true", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM subjects WHERE created_at IS NOT NULL AND updated_at=created_at AND timestamps_approximate=true", Integer.class));
            assertFalse(jdbc.queryForObject("SELECT created_at FROM users WHERE id=1", java.time.LocalDateTime.class).isBefore(before));
            assertEquals("retained-hash", jdbc.queryForObject("SELECT password FROM users WHERE id=1", String.class));
            assertEquals(session, jdbc.queryForMap("SELECT * FROM refresh_tokens"));
            jdbc.update("INSERT INTO users(id,email,is_deleted) VALUES(3,'new@example.test',false)");
            jdbc.update("INSERT INTO subjects(id,code,name) VALUES(2,'NEW','New subject')");
            assertFalse(jdbc.queryForObject("SELECT timestamps_approximate FROM users WHERE id=3", Boolean.class));
            assertFalse(jdbc.queryForObject("SELECT timestamps_approximate FROM subjects WHERE id=2", Boolean.class));
            assertEquals(0, Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).target("6").load().migrate().migrationsExecuted);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void refusesAmbiguousLegacyLogicalIdentitiesBeforeCreatingParentTable(boolean mixedOwners) {
        try (MySQLContainer<?> db = new MySQLContainer<>("mysql:8.4")) {
            db.start();
            Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                    .target("2").load().migrate();
            JdbcTemplate jdbc = jdbc(db);
            jdbc.update("INSERT INTO users (id,email) VALUES (1,'one@example.test'),(2,'two@example.test')");
            jdbc.update("INSERT INTO school (id,name) VALUES (1,'School')");
            jdbc.update("INSERT INTO departments (id,name,school_id) VALUES (1,'Department',1)");
            jdbc.update("INSERT INTO posts (post_id,version,title,content,status,user_id,department_id,"
                    + "is_latest_version,created_at,updated_at) VALUES "
                    + "(7,1,'First','Body','APPROVED',1,1,true,NOW(),NOW()),"
                    + "(7,?,'Ambiguous','Body','APPROVED',?,1,true,NOW(),NOW())",
                    mixedOwners ? 2 : 1, mixedOwners ? 2 : 1);
            assertThrows(Exception.class, () -> flyway(db).migrate());
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema=DATABASE() AND table_name='post_identities'", Integer.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM posts", Integer.class));
        }
    }

    @Test
    void upgradesV6WithNullableCoverWithoutChangingExistingProfileOrSession() {
        try (MySQLContainer<?> db = new MySQLContainer<>("mysql:8.4")) {
            db.start();
            Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).target("6").load().migrate();
            JdbcTemplate jdbc = jdbc(db);
            jdbc.update("INSERT INTO users(id,email,password,profile_picture) VALUES(1,'cover@example.test','retained-hash','profile-pictures/old.png')");
            jdbc.update("INSERT INTO refresh_tokens(user_id,token,expiry_date) VALUES(1,'retained-session','2027-01-01')");
            var before = jdbc.queryForMap("SELECT * FROM users WHERE id=1");
            var session = jdbc.queryForMap("SELECT * FROM refresh_tokens");
            Flyway coverMigration = Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).target("7").load();
            assertEquals(1, coverMigration.migrate().migrationsExecuted);
            var after = jdbc.queryForMap("SELECT * FROM users WHERE id=1");
            assertNull(after.remove("cover_picture"));
            assertEquals(before, after);
            assertEquals(session, jdbc.queryForMap("SELECT * FROM refresh_tokens"));
            assertEquals(0, coverMigration.migrate().migrationsExecuted);
        }
    }

    @Test
    void upgradesV7WithVersionScopedLinksAndConstraintsWithoutChangingPosts() {
        try (MySQLContainer<?> db = new MySQLContainer<>("mysql:8.4")) {
            db.start();
            Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).target("7").load().migrate();
            JdbcTemplate jdbc = jdbc(db);
            jdbc.update("INSERT INTO users(id,email) VALUES(1,'links@example.test')");
            jdbc.update("INSERT INTO school(id,name) VALUES(1,'School')");
            jdbc.update("INSERT INTO departments(id,name,school_id) VALUES(1,'Department',1)");
            jdbc.update("INSERT INTO post_identities(id,owner_id) VALUES(7,1)");
            jdbc.update("INSERT INTO posts(id,post_id,version,title,content,status,user_id,department_id,is_latest_version,created_at,updated_at) "
                    + "VALUES(10,7,1,'Legacy','Content','APPROVED',1,1,true,NOW(),NOW()),(11,7,2,'Pending','Body','PENDING_APPROVAL',1,1,true,NOW(),NOW())");
            jdbc.update("INSERT INTO files(post_id,user_id,file_name) VALUES(10,1,'retained.pdf')");
            var before = jdbc.queryForList("SELECT * FROM posts ORDER BY id");
            Flyway linksMigration = Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).target("8").load();
            assertEquals(1, linksMigration.migrate().migrationsExecuted);
            assertEquals(before, jdbc.queryForList("SELECT * FROM posts ORDER BY id"));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class));
            String insert = "INSERT INTO post_links(post_id,url,normalized_url,normalized_hash,position) VALUES(?,'https://example.test/','https://example.test/',?,?)";
            jdbc.update(insert, 10, "a".repeat(64), 0);
            jdbc.update(insert, 11, "a".repeat(64), 0);
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(insert, 10, "a".repeat(64), 1));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(insert, 10, "b".repeat(64), 0));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(insert, 999, "b".repeat(64), 0));
            var invalidPosition = assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update(insert, 10, "b".repeat(64), 10));
            assertEquals(3819, ((java.sql.SQLException) invalidPosition.getCause()).getErrorCode());
            jdbc.update("UPDATE posts SET is_deleted=true WHERE id=10");
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM post_links", Integer.class));
            jdbc.update("DELETE FROM posts WHERE id=11");
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM post_links", Integer.class));
            assertEquals(0, linksMigration.migrate().migrationsExecuted);
        }
    }

    @Test
    void upgradesV8WithoutInventingLegacyApprovalDatesOrChangingStoredData() {
        try (MySQLContainer<?> db = new MySQLContainer<>("mysql:8.4")) {
            db.start();
            Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).target("8").load().migrate();
            JdbcTemplate jdbc = jdbc(db);
            jdbc.update("INSERT INTO users(id,email) VALUES(1,'legacy@example.test')");
            jdbc.update("INSERT INTO school(id,name) VALUES(1,'School')");
            jdbc.update("INSERT INTO departments(id,name,school_id) VALUES(1,'Department',1)");
            jdbc.update("INSERT INTO post_identities(id,owner_id) VALUES(1,1)");
            jdbc.update("INSERT INTO posts(id,post_id,version,title,content,status,user_id,department_id,is_latest_version,created_at,updated_at) "
                    + "VALUES(1,1,1,'Old','Body','SUPERSEDED',1,1,false,'2026-01-01','2026-01-02'),"
                    + "(2,1,2,'New','Body','APPROVED',1,1,true,'2026-01-03','2026-01-04')");
            var before = jdbc.queryForList("SELECT * FROM posts ORDER BY id");
            Flyway approvalMigration = Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).target("9").load();
            assertEquals(1, approvalMigration.migrate().migrationsExecuted);
            var after = jdbc.queryForList("SELECT * FROM posts ORDER BY id");
            after.forEach(row -> assertNull(row.remove("approved_at")));
            assertEquals(before, after);
            assertEquals(0, approvalMigration.migrate().migrationsExecuted);
        }
    }

    @Test
    void upgradesV9WithEmptyAuditTrailAndRetainsNumericActorsAfterPhysicalAccountRemoval() {
        try (MySQLContainer<?> db = new MySQLContainer<>("mysql:8.4")) {
            db.start();
            Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()).target("9").load().migrate();
            JdbcTemplate jdbc = jdbc(db);
            jdbc.update("INSERT INTO users(id,email,password) VALUES(1,'retained@example.test','retained-hash')");
            jdbc.update("INSERT INTO refresh_tokens(user_id,token,expiry_date) VALUES(1,'retained-session','2027-01-01')");
            var before = jdbc.queryForList("SELECT * FROM refresh_tokens");
            assertEquals(1, flyway(db).migrate().migrationsExecuted);
            assertEquals(before, jdbc.queryForList("SELECT * FROM refresh_tokens"));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class));
            jdbc.update("INSERT INTO audit_events(actor_id,action,target_type,target_id,outcome,occurred_at,correlation_id,metadata) VALUES(1,'USER_DELETED','USER',1,'SUCCESS',UTC_TIMESTAMP(6),'migration','{}')");
            jdbc.update("DELETE FROM refresh_tokens"); jdbc.update("DELETE FROM users WHERE id=1");
            assertEquals(1L, jdbc.queryForObject("SELECT actor_id FROM audit_events", Long.class));
            assertEquals(0, flyway(db).migrate().migrationsExecuted);
        }
    }

    private JdbcTemplate jdbc(MySQLContainer<?> db) {
        return new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()));
    }
}
