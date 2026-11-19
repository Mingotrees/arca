package com.popman.arca.repository;

import com.popman.arca.dto.v1.post.TrendingPostResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.List;

@Repository
public class TrendingRepository {
    static final String TRENDING_SQL = "SELECT p.id,p.post_id,p.title,p.user_id,p.department_id,p.approved_at,"
            + "COALESCE(rv.upvotes,0) AS recent_upvotes,COALESCE(rv.downvotes,0) AS recent_downvotes "
            + "FROM posts p LEFT JOIN (SELECT post_id,COUNT(CASE WHEN vote_type='UPVOTE' THEN 1 END) AS upvotes,"
            + "COUNT(CASE WHEN vote_type='DOWNVOTE' THEN 1 END) AS downvotes FROM votes WHERE changed_at>=? AND changed_at<? GROUP BY post_id) rv ON rv.post_id=p.id "
            + "WHERE p.is_deleted=false AND p.is_latest_version=true AND p.status='APPROVED' AND p.approved_at>=? AND p.approved_at<? "
            + "AND NOT EXISTS (SELECT 1 FROM posts newer WHERE newer.post_id=p.post_id AND newer.version>p.version "
            + "AND newer.is_deleted=false AND newer.is_latest_version=true AND newer.status='APPROVED') "
            + "ORDER BY (1.0+COALESCE(rv.upvotes,0))/((1.0+COALESCE(rv.downvotes,0))*POWER(2.0+TIMESTAMPDIFF(MICROSECOND,p.approved_at,?)/3600000000.0,1.5)) DESC,"
            + "p.approved_at DESC,p.id DESC LIMIT ?";
    private final JdbcTemplate jdbc;
    public TrendingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<TrendingPostResponse> find(Instant from, Instant to, int limit) {
        var start = LocalDateTime.ofInstant(from, ZoneOffset.UTC); var end = LocalDateTime.ofInstant(to, ZoneOffset.UTC);
        return jdbc.query(TRENDING_SQL, (r,n) -> new TrendingPostResponse(r.getLong("id"),r.getInt("post_id"),r.getString("title"),
                r.getLong("user_id"),r.getLong("department_id"),r.getObject("approved_at",LocalDateTime.class).toInstant(ZoneOffset.UTC),
                r.getLong("recent_upvotes"),r.getLong("recent_downvotes")), start,end,start,end,end,limit);
    }
}
