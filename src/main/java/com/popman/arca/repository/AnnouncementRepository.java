package com.popman.arca.repository;

import com.popman.arca.dto.v1.announcement.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.*;
import java.util.List;
import java.util.Optional;

@Repository
public class AnnouncementRepository {
    static final String ACTIVE_SQL = "SELECT * FROM announcements WHERE is_deleted=false AND active=true "
            + "AND (starts_at IS NULL OR starts_at<=?) AND (ends_at IS NULL OR ends_at>?) "
            + "ORDER BY priority DESC,COALESCE(starts_at,created_at) DESC,id DESC LIMIT ?";
    private final JdbcTemplate jdbc;
    public AnnouncementRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long create(AnnouncementRequest request, long actor, Instant now) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var sql = connection.prepareStatement("INSERT INTO announcements(title,body,url,priority,active,starts_at,ends_at,created_by,updated_by,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            Object[] args = {request.title(), request.body(), request.url(), request.priority(), request.active(), utc(request.startsAt()),
                    utc(request.endsAt()), actor, actor, utc(now), utc(now)};
            for (int i = 0; i < args.length; i++) sql.setObject(i + 1, args[i]);
            return sql;
        }, keys);
        return java.util.Objects.requireNonNull(keys.getKey()).longValue();
    }
    public Optional<Row> find(long id, boolean lock) {
        return jdbc.query("SELECT * FROM announcements WHERE id=?" + (lock ? " FOR UPDATE" : ""), MAPPER, id).stream().findFirst();
    }
    public void replace(long id, AnnouncementRequest request, long actor, Instant now) {
        jdbc.update("UPDATE announcements SET title=?,body=?,url=?,priority=?,active=?,starts_at=?,ends_at=?,updated_by=?,updated_at=? WHERE id=?",
                request.title(), request.body(), request.url(), request.priority(), request.active(), utc(request.startsAt()), utc(request.endsAt()), actor, utc(now), id);
    }
    public void delete(long id, long actor, Instant now) {
        jdbc.update("UPDATE announcements SET is_deleted=true,active=false,deleted_at=?,updated_at=?,updated_by=? WHERE id=?",
                utc(now), utc(now), actor, id);
    }
    public AnnouncementPage page(int page, int size) {
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM announcements WHERE is_deleted=false", Long.class);
        var rows = jdbc.query("SELECT * FROM announcements WHERE is_deleted=false ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?", MAPPER, size, (long) page * size);
        return new AnnouncementPage(rows.stream().map(Row::admin).toList(), page, size, count, (count + size - 1) / size);
    }
    public List<AnnouncementResponse> active(Instant now, int limit) {
        return jdbc.query(ACTIVE_SQL, MAPPER, utc(now), utc(now), limit).stream().map(Row::publicResponse).toList();
    }
    private static LocalDateTime utc(Instant value) { return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
    private static Instant instant(ResultSet r, String column) throws SQLException {
        LocalDateTime value = r.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
    private static final RowMapper<Row> MAPPER = (r, n) -> new Row(r.getLong("id"), r.getString("title"), r.getString("body"), r.getString("url"),
            r.getInt("priority"), r.getBoolean("active"), instant(r, "starts_at"), instant(r, "ends_at"), r.getLong("created_by"),
            r.getLong("updated_by"), instant(r, "created_at"), instant(r, "updated_at"), r.getBoolean("is_deleted"));
    public record Row(long id, String title, String body, String url, int priority, boolean active, Instant startsAt, Instant endsAt,
                      long createdBy, long updatedBy, Instant createdAt, Instant updatedAt, boolean deleted) {
        public AdminAnnouncementResponse admin() { return new AdminAnnouncementResponse(id,title,body,url,priority,active,startsAt,endsAt,createdBy,updatedBy,createdAt,updatedAt); }
        public AnnouncementResponse publicResponse() { return new AnnouncementResponse(id,title,body,url,priority,startsAt,endsAt); }
    }
}
