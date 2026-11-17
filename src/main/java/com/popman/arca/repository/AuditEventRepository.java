package com.popman.arca.repository;

import com.popman.arca.dto.v1.admin.AuditEventPage;
import com.popman.arca.dto.v1.admin.AuditEventResponse;
import com.popman.arca.service.audit.AuditAction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Map;

@Repository
public class AuditEventRepository {
    static final String SELECT_COLUMNS = "SELECT id,actor_id,action,target_type,target_id,outcome,occurred_at,correlation_id,JSON_UNQUOTE(JSON_EXTRACT(metadata,'$.role')) AS role";
    private final JdbcTemplate jdbc;
    public AuditEventRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(AuditAction action, Long actor, Long target, String outcome, String correlation) {
        insert(action, actor, target, outcome, correlation, "{}");
    }

    public void insertRole(AuditAction action, Long actor, Long target, String correlation, String role) {
        if (role == null || role.length() > 255 || !role.matches("ROLE_[A-Z_]+")) throw new IllegalArgumentException("Invalid audit role");
        insert(action, actor, target, "SUCCESS", correlation, "{\"role\":\"" + role + "\"}");
    }

    private void insert(AuditAction action, Long actor, Long target, String outcome, String correlation, String metadata) {
        jdbc.update("INSERT INTO audit_events(actor_id,action,target_type,target_id,outcome,occurred_at,correlation_id,metadata) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6),?,?)",
                actor, action.name(), action.targetType(), target, outcome, correlation, metadata);
    }

    public AuditEventPage query(int page, int size, String action, String outcome, String targetType,
                               Long actorId, Long targetId, LocalDateTime from, LocalDateTime to) {
        StringBuilder where = new StringBuilder(" FROM audit_events WHERE occurred_at>=? AND occurred_at<?");
        var args = new ArrayList<Object>(); args.add(from); args.add(to);
        String[] names = {"action", "outcome", "target_type", "actor_id", "target_id"};
        Object[] values = {action, outcome, targetType, actorId, targetId};
        for (int i = 0; i < names.length; i++) if (values[i] != null) {
            where.append(" AND ").append(names[i]).append("=?"); args.add(values[i]);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        args.add(size); args.add((long) page * size);
        var content = jdbc.query(SELECT_COLUMNS + where
                        + " ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?",
                (r, n) -> new AuditEventResponse(r.getLong("id"), r.getObject("actor_id", Long.class), r.getString("action"),
                        r.getString("target_type"), r.getObject("target_id", Long.class), r.getString("outcome"),
                        r.getObject("occurred_at", LocalDateTime.class).toInstant(ZoneOffset.UTC), r.getString("correlation_id"), roleMetadata(r.getString("role"))), args.toArray());
        return new AuditEventPage(content, page, size, total, (total + size - 1) / size);
    }

    private Map<String, Object> roleMetadata(String role) {
        return role != null && role.length() <= 255 && role.matches("ROLE_[A-Z_]+") ? Map.of("role", role) : Map.of();
    }
}
