package com.popman.arca.service;

import com.popman.arca.dto.v1.admin.AdminDashboardResponse;
import com.popman.arca.repository.AdminDashboardRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Service
public class AdminDashboardService {
    private static final Instant MYSQL_MIN = Instant.parse("1000-01-01T00:00:00Z");
    private static final Instant MYSQL_END = Instant.parse("+10000-01-01T00:00:00Z");
    private final AdminDashboardRepository repository;
    public AdminDashboardService(AdminDashboardRepository repository) { this.repository = repository; }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminDashboardResponse dashboard(OffsetDateTime from, OffsetDateTime to) {
        Instant calculatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant end = to == null ? calculatedAt : to.toInstant();
        Instant start = from == null ? end.minus(Duration.ofDays(90)) : from.toInstant();
        Duration range = Duration.between(start, end);
        if (range.isZero() || range.isNegative() || range.compareTo(Duration.ofDays(90)) > 0)
            throw new IllegalArgumentException("Window must be positive and no longer than 90 days");
        if (start.isBefore(MYSQL_MIN) || !end.isBefore(MYSQL_END))
            throw new IllegalArgumentException("Window must be within MySQL years 1000 through 9999");
        if (start.getNano() % 1000 != 0 || end.getNano() % 1000 != 0)
            throw new IllegalArgumentException("Window supports at most microsecond precision");
        return repository.dashboard(LocalDateTime.ofInstant(start, ZoneOffset.UTC),
                LocalDateTime.ofInstant(end, ZoneOffset.UTC), calculatedAt);
    }
}
