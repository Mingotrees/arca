package com.popman.arca.repository;

import com.popman.arca.dto.v1.post.PostSearchCriteria;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class PostSearchRepository {
    private static final String MATCH = "MATCH(p.title,p.content) AGAINST (:q IN NATURAL LANGUAGE MODE)";
    private final NamedParameterJdbcTemplate jdbc;

    public PostSearchRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public SearchResult search(PostSearchCriteria criteria) {
        SearchSql query = buildQuery(criteria);
        long total = jdbc.queryForObject(query.countSql(), query.parameters(), Long.class);
        if ((long) criteria.page() * criteria.size() >= total) return new SearchResult(List.of(), total);
        return new SearchResult(jdbc.queryForList(query.pageSql(), query.parameters(), Long.class), total);
    }

    SearchSql buildQuery(PostSearchCriteria criteria) {
        String status = criteria.status() == null ? "APPROVED" : criteria.status();
        var parameters = new MapSqlParameterSource("status", status)
                .addValue("limit", criteria.size()).addValue("offset", (long) criteria.page() * criteria.size());
        StringBuilder where = new StringBuilder(" FROM posts p WHERE p.is_deleted=false AND p.status=:status");
        if (status.equals("APPROVED")) where.append(" AND p.is_latest_version=true");
        else where.append(" AND p.version=(SELECT MAX(revision.version) FROM posts revision "
                + "WHERE revision.post_id=p.post_id AND revision.is_deleted=false)");
        if (criteria.q() != null) {
            where.append(" AND ").append(MATCH).append(" > 0");
            parameters.addValue("q", criteria.q());
        }
        if (criteria.subjectId() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM post_tags tag WHERE tag.post_id=p.id AND tag.subject_id=:subject)");
            parameters.addValue("subject", criteria.subjectId());
        }
        if (criteria.departmentId() != null) {
            where.append(" AND p.department_id=:department"); parameters.addValue("department", criteria.departmentId());
        }
        if (criteria.authorId() != null) {
            where.append(" AND p.user_id=:author"); parameters.addValue("author", criteria.authorId());
        }
        if (criteria.from() != null) { where.append(" AND p.created_at>=:from"); parameters.addValue("from", criteria.from()); }
        if (criteria.to() != null) { where.append(" AND p.created_at<:to"); parameters.addValue("to", criteria.to()); }
        // Only literal, reviewed fragments enter ORDER BY; no user-supplied SQL identifiers.
        String order = switch (criteria.sort()) {
            case "oldest" -> "p.created_at ASC,p.id ASC";
            case "relevance" -> MATCH + " DESC,p.id DESC";
            case "vote_score" -> "COALESCE((SELECT SUM(CASE WHEN v.vote_type='UPVOTE' THEN 1 "
                    + "WHEN v.vote_type='DOWNVOTE' THEN -1 ELSE 0 END) FROM votes v WHERE v.post_id=p.id),0) DESC,p.id DESC";
            default -> "p.created_at DESC,p.id DESC";
        };
        return new SearchSql("SELECT p.id" + where + " ORDER BY " + order + " LIMIT :limit OFFSET :offset",
                "SELECT COUNT(*)" + where, parameters);
    }

    record SearchSql(String pageSql, String countSql, MapSqlParameterSource parameters) {}
    public record SearchResult(List<Long> ids, long total) {}
}
