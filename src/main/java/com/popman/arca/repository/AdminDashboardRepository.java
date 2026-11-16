package com.popman.arca.repository;

import com.popman.arca.dto.v1.admin.AdminDashboardResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@Repository
public class AdminDashboardRepository {
    private static final String ELIGIBLE = "p.is_deleted=false AND p.is_latest_version=true AND p.status='APPROVED'";
    static final String USERS_SQL = "SELECT COUNT(CASE WHEN is_deleted=false OR is_deleted IS NULL THEN 1 END) AS active, "
            + "COUNT(CASE WHEN is_deleted=true THEN 1 END) AS deleted FROM users";
    static final String ROLES_SQL = "SELECT r.role,COUNT(DISTINCT u.id) AS total FROM user_roles r JOIN users u ON u.id=r.user_id "
            + "WHERE (u.is_deleted=false OR u.is_deleted IS NULL) AND r.role IS NOT NULL GROUP BY r.role ORDER BY r.role";
    static final String STATUS_SQL = "SELECT p.status,p.is_deleted,COUNT(*) AS total FROM posts p "
            + "WHERE p.version=(SELECT MAX(v.version) FROM posts v WHERE v.post_id=p.post_id) "
            + "GROUP BY p.status,p.is_deleted ORDER BY p.status,p.is_deleted";
    static final String ACTIVITY_SQL = "SELECT "
            + "COUNT(CASE WHEN version=1 AND created_at>=? AND created_at<? THEN 1 END) AS created, "
            + "COUNT(CASE WHEN approved_at>=? AND approved_at<? THEN 1 END) AS approved, "
            + "COUNT(CASE WHEN approved_at IS NULL AND status IN ('APPROVED','SUPERSEDED') THEN 1 END) AS unknown_date FROM posts";
    static final String FILES_SQL = "SELECT COUNT(*) AS total,COALESCE(SUM(CASE WHEN f.file_size>=0 THEN f.file_size ELSE 0 END),0) AS bytes, "
            + "COUNT(CASE WHEN f.file_size IS NULL OR f.file_size<0 THEN 1 END) AS unknown_size "
            + "FROM files f JOIN posts p ON p.id=f.post_id WHERE " + ELIGIBLE;
    static final String VOTES_SQL = "SELECT COUNT(CASE WHEN v.vote_type='UPVOTE' THEN 1 END) AS upvotes, "
            + "COUNT(CASE WHEN v.vote_type='DOWNVOTE' THEN 1 END) AS downvotes "
            + "FROM votes v JOIN posts p ON p.id=v.post_id WHERE " + ELIGIBLE;
    static final String VAULTS_SQL = "SELECT COUNT(*) FROM vaults v JOIN posts p ON p.id=v.post_id WHERE " + ELIGIBLE;

    private final JdbcTemplate jdbc;
    public AdminDashboardRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public AdminDashboardResponse dashboard(LocalDateTime from, LocalDateTime to, Instant calculatedAt) {
        long[] users = jdbc.queryForObject(USERS_SQL, (r, n) -> new long[]{r.getLong("active"), r.getLong("deleted")});
        Map<String, Long> roles = new LinkedHashMap<>();
        jdbc.query(ROLES_SQL, (org.springframework.jdbc.core.RowCallbackHandler) r -> roles.put(r.getString("role"), r.getLong("total")));
        Map<String, Long> statuses = new LinkedHashMap<>();
        long[] posts = new long[2];
        jdbc.query(STATUS_SQL, (org.springframework.jdbc.core.RowCallbackHandler) r -> {
            long total = r.getLong("total");
            if (r.getBoolean("is_deleted")) posts[1] += total;
            else { posts[0] += total; statuses.merge(r.getString("status"), total, Long::sum); }
        });
        long[] activity = jdbc.queryForObject(ACTIVITY_SQL,
                (r, n) -> new long[]{r.getLong("created"), r.getLong("approved"), r.getLong("unknown_date")}, from, to, from, to);
        long[] files = jdbc.queryForObject(FILES_SQL,
                (r, n) -> new long[]{r.getLong("total"), r.getLong("bytes"), r.getLong("unknown_size")});
        long[] votes = jdbc.queryForObject(VOTES_SQL, (r, n) -> new long[]{r.getLong("upvotes"), r.getLong("downvotes")});
        long vaults = jdbc.queryForObject(VAULTS_SQL, Long.class);
        return new AdminDashboardResponse(calculatedAt, "UTC", from.toInstant(ZoneOffset.UTC), to.toInstant(ZoneOffset.UTC),
                new AdminDashboardResponse.Users(users[0], users[1], roles),
                new AdminDashboardResponse.Posts(posts[0], posts[1], statuses, activity[0], activity[1], activity[2]),
                new AdminDashboardResponse.Moderation(statuses.getOrDefault("PENDING_APPROVAL", 0L)),
                new AdminDashboardResponse.Engagement(files[0], files[1], files[2], votes[0], votes[1], vaults));
    }
}
