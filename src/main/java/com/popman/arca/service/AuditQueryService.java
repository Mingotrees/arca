package com.popman.arca.service;

import com.popman.arca.dto.v1.admin.AuditEventPage;
import com.popman.arca.repository.AuditEventRepository;
import com.popman.arca.service.audit.AuditAction;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Set;

@Service
public class AuditQueryService {
    private final AuditEventRepository events;
    public AuditQueryService(AuditEventRepository events) { this.events = events; }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AuditEventPage query(Integer page, Integer size, String action, String outcome, String targetType,
                                Long actorId, Long targetId, OffsetDateTime from, OffsetDateTime to) {
        int p = page == null ? 0 : page, s = size == null ? 20 : size;
        if (p < 0 || p > 10000 || s < 1 || s > 100) throw new IllegalArgumentException("Page must be 0–10000 and size 1–100");
        if (action != null) AuditAction.valueOf(action);
        if (outcome != null && !Set.of("SUCCESS", "DENIED", "ERROR", "ACCEPTED").contains(outcome)) throw new IllegalArgumentException("Unsupported outcome");
        if (targetType != null && Arrays.stream(AuditAction.values()).noneMatch(a -> a.targetType().equals(targetType))) throw new IllegalArgumentException("Unsupported target_type");
        if ((actorId != null && actorId < 1) || (targetId != null && (targetId < 1 || targetType == null))) throw new IllegalArgumentException("Positive IDs required; target_id requires target_type");
        Instant end = to == null ? Instant.now().truncatedTo(ChronoUnit.MICROS) : to.toInstant();
        Instant start = from == null ? end.minus(Duration.ofDays(90)) : from.toInstant();
        Duration duration = Duration.between(start, end);
        if (duration.isZero() || duration.isNegative() || duration.compareTo(Duration.ofDays(90)) > 0
                || start.isBefore(Instant.parse("1000-01-01T00:00:00Z")) || !end.isBefore(Instant.parse("+10000-01-01T00:00:00Z"))
                || start.getNano() % 1000 != 0 || end.getNano() % 1000 != 0) throw new IllegalArgumentException("Invalid UTC window: positive, at most 90 days, years 1000–9999, microsecond precision");
        return events.query(p, s, action, outcome, targetType, actorId, targetId,
                LocalDateTime.ofInstant(start, ZoneOffset.UTC), LocalDateTime.ofInstant(end, ZoneOffset.UTC));
    }
}
