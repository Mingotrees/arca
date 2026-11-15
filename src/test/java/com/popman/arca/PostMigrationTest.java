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
            assertEquals(5, flyway.migrate().migrationsExecuted);
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
            assertEquals(4, baseline.migrate().migrationsExecuted);
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
            assertEquals(1, flyway(db).migrate().migrationsExecuted);
            assertEquals(before, jdbc.queryForMap("SELECT * FROM refresh_tokens"));
            assertEquals("retained-bcrypt", jdbc.queryForObject("SELECT password FROM users WHERE id=1", String.class));
            jdbc.update("INSERT INTO password_reset_tokens(user_id,token_hash,created_at,expires_at) VALUES(1,?,UTC_TIMESTAMP(6),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 15 MINUTE))", "a".repeat(64));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                    "INSERT INTO password_reset_tokens(user_id,token_hash,created_at,expires_at) VALUES(1,?,UTC_TIMESTAMP(6),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 15 MINUTE))", "a".repeat(64)));
            jdbc.update("DELETE FROM refresh_tokens"); jdbc.update("DELETE FROM users WHERE id=1");
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens", Integer.class));
            assertEquals(0, flyway(db).migrate().migrationsExecuted);
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

    private JdbcTemplate jdbc(MySQLContainer<?> db) {
        return new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword()));
    }
}
