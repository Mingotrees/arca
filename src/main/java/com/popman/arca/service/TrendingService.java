package com.popman.arca.service;

import com.popman.arca.config.HomepageProperties;
import com.popman.arca.dto.v1.post.TrendingResponse;
import com.popman.arca.repository.TrendingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.temporal.ChronoUnit;

@Service
public class TrendingService {
    private final TrendingRepository repository;
    private final HomepageProperties flags;
    private final Clock clock;
    public TrendingService(TrendingRepository repository, HomepageProperties flags, Clock clock) { this.repository=repository; this.flags=flags; this.clock=clock; }
    @PreAuthorize("hasAnyRole('USER','ADMIN')") @Transactional(readOnly = true)
    public TrendingResponse trending(String window, Integer limit) {
        if (!flags.trendingEnabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trending not enabled");
        String selected = window == null ? "7d" : window;
        Duration duration = switch (selected) { case "24h" -> Duration.ofDays(1); case "7d" -> Duration.ofDays(7); case "30d" -> Duration.ofDays(30); default -> throw new IllegalArgumentException("Window must be 24h, 7d or 30d"); };
        int count = limit == null ? 10 : limit;
        if (count < 1 || count > 50) throw new IllegalArgumentException("Limit must be 1–50");
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return new TrendingResponse(now, selected, repository.find(now.minus(duration), now, count));
    }
}
