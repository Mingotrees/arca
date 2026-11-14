package db.migration;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

/** Fail closed on ambiguous legacy ownership before installing identity constraints. */
public class V3__Logical_post_identity extends BaseJavaMigration {
    @Override
    public boolean canExecuteInTransaction() { return false; }

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement sql = context.getConnection().createStatement()) {
            rejectRows(sql, "SELECT post_id FROM posts GROUP BY post_id HAVING COUNT(DISTINCT user_id) > 1 LIMIT 1",
                    "Mixed owners share a logical post_id; reconcile legacy identities before migrating");
            rejectRows(sql, "SELECT post_id FROM posts GROUP BY post_id,version HAVING COUNT(*) > 1 LIMIT 1",
                    "Duplicate logical post/version pairs; reconcile legacy identities before migrating");
            rejectRows(sql, "SELECT post_id FROM posts WHERE post_id <= 0 LIMIT 1",
                    "Non-positive logical post_id; reconcile legacy identities before migrating");
            sql.execute("CREATE TABLE post_identities (id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                    + "owner_id BIGINT NOT NULL, CONSTRAINT uk_post_identity_owner UNIQUE (id,owner_id)) ENGINE=InnoDB");
            sql.execute("INSERT INTO post_identities (id,owner_id) SELECT post_id,MIN(user_id) FROM posts GROUP BY post_id");
            sql.execute("ALTER TABLE posts ADD CONSTRAINT uk_posts_logical_version UNIQUE (post_id,version), "
                    + "ADD CONSTRAINT fk_posts_identity_owner FOREIGN KEY (post_id,user_id) "
                    + "REFERENCES post_identities(id,owner_id)");
        }
    }

    private void rejectRows(Statement sql, String query, String message) throws Exception {
        try (var rows = sql.executeQuery(query)) {
            if (rows.next()) throw new FlywayException(message);
        }
    }
}
