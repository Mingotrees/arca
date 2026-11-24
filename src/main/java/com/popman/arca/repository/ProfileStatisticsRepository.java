package com.popman.arca.repository;

import com.popman.arca.dto.v1.user.UserStatsResponse;
import com.popman.arca.dto.v1.subject.SubjectStatsResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

@Repository
public class ProfileStatisticsRepository {
    private static final String ELIGIBLE = "p.is_deleted=false AND p.is_latest_version=true AND p.status='APPROVED'";
    static final String USER_STATS_SQL = "SELECT COUNT(DISTINCT p.post_id) AS approved_post_count, "
            + "COALESCE(SUM(CASE WHEN v.vote_type='UPVOTE' THEN 1 ELSE 0 END),0) AS upvotes_received, "
            + "COALESCE(SUM(CASE WHEN v.vote_type='DOWNVOTE' THEN 1 ELSE 0 END),0) AS downvotes_received "
            + "FROM posts p LEFT JOIN votes v ON v.post_id=p.id WHERE p.user_id=? AND " + ELIGIBLE;
    static final String SUBJECT_STATS_SQL = "SELECT COUNT(DISTINCT p.post_id) AS approved_post_count, "
            + "MAX(p.updated_at) AS last_approved_post_at FROM posts p WHERE " + ELIGIBLE
            + " AND EXISTS (SELECT 1 FROM post_tags tag WHERE tag.post_id=p.id AND tag.subject_id=?)";
    private final JdbcTemplate jdbc;
    public ProfileStatisticsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean activeUserExists(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND is_deleted=false", Long.class, id) > 0;
    }
    public boolean subjectExists(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM subjects WHERE id=?", Long.class, id) > 0;
    }
    public UserStatsResponse user(long id) {
        return jdbc.queryForObject(USER_STATS_SQL, (row, number) -> new UserStatsResponse(row.getLong("approved_post_count"),
                row.getLong("upvotes_received"), row.getLong("downvotes_received")), id);
    }
    public SubjectStatsResponse subject(long id) {
        return jdbc.queryForObject(SUBJECT_STATS_SQL, (row, number) -> new SubjectStatsResponse(row.getLong("approved_post_count"),
                row.getObject("last_approved_post_at", LocalDateTime.class)), id);
    }
}
