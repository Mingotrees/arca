package com.popman.arca.service;

import com.popman.arca.config.HomepageProperties;
import com.popman.arca.dto.v1.announcement.*;
import com.popman.arca.dto.v1.post.PostLinkRequest;
import com.popman.arca.repository.AnnouncementRepository;
import com.popman.arca.service.audit.*;
import com.popman.arca.service.links.PostLinkValidator;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AnnouncementService {
    private final AnnouncementRepository repository;
    private final HomepageProperties flags;
    private final AuditService audit;
    private final Clock clock;
    private final PostLinkValidator links = new PostLinkValidator();
    public AnnouncementService(AnnouncementRepository repository, HomepageProperties flags, AuditService audit, Clock clock) {
        this.repository = repository; this.flags = flags; this.audit = audit; this.clock = clock;
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional
    public AdminAnnouncementResponse create(AnnouncementRequest request) {
        enabled(); var normalized = validate(request); long actor = actor();
        long id = repository.create(normalized, actor, now());
        audit.success(AuditAction.ANNOUNCEMENT_CREATED, id);
        return repository.find(id, false).orElseThrow().admin();
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional
    public AdminAnnouncementResponse replace(long id, AnnouncementRequest request) {
        enabled(); var existing = row(id, true); if (existing.deleted()) throw missing();
        repository.replace(id, validate(request), actor(), now());
        audit.success(AuditAction.ANNOUNCEMENT_UPDATED, id);
        return repository.find(id, false).orElseThrow().admin();
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional
    public void delete(long id) {
        enabled(); var existing = row(id, true); if (existing.deleted()) return;
        repository.delete(id, actor(), now()); audit.success(AuditAction.ANNOUNCEMENT_DELETED, id);
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional(readOnly = true)
    public AdminAnnouncementResponse get(long id) {
        enabled(); var found = row(id, false); if (found.deleted()) throw missing(); return found.admin();
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AnnouncementPage page(Integer page, Integer size) {
        enabled(); int p = page == null ? 0 : page, s = size == null ? 20 : size;
        if (p < 0 || p > 10000 || s < 1 || s > 100) throw new IllegalArgumentException("Page 0–10000, size 1–100 required");
        return repository.page(p, s);
    }
    @PreAuthorize("hasAnyRole('USER','ADMIN')") @Transactional(readOnly = true)
    public List<AnnouncementResponse> active(Integer limit) {
        enabled(); int count = limit == null ? 10 : limit;
        if (count < 1 || count > 50) throw new IllegalArgumentException("Limit must be 1–50");
        return repository.active(now(), count);
    }
    private AnnouncementRequest validate(AnnouncementRequest value) {
        String title = text(value.title(), 128, false), body = text(value.body(), 4096, true);
        if (title == null) throw new IllegalArgumentException("Title required");
        String url = value.url() == null || value.url().isBlank() ? null : links.validate(List.of(new PostLinkRequest(value.url(), null))).getFirst().getUrl();
        if (body == null && url == null) throw new IllegalArgumentException("Body or URL required");
        int priority = value.priority() == null ? 0 : value.priority();
        if (priority < 0 || priority > 1000) throw new IllegalArgumentException("Priority must be 0–1000");
        timestamp(value.startsAt()); timestamp(value.endsAt());
        if (value.startsAt() != null && value.endsAt() != null && !value.endsAt().isAfter(value.startsAt())) throw new IllegalArgumentException("ends_at must exceed starts_at");
        return new AnnouncementRequest(title, body, url, priority, Boolean.TRUE.equals(value.active()), value.startsAt(), value.endsAt());
    }
    private String text(String value, int max, boolean multiline) {
        if (value == null) return null;
        if (value.length() > max || value.codePoints().anyMatch(c -> (c >= 0xD800 && c <= 0xDFFF)
                || (Character.isISOControl(c) && !(multiline && (c == '\n' || c == '\r' || c == '\t')))))
            throw new IllegalArgumentException("Invalid announcement text");
        String stripped = value.strip(); return stripped.isEmpty() ? null : stripped;
    }
    private void timestamp(Instant value) {
        if (value != null && (value.isBefore(Instant.parse("1000-01-01T00:00:00Z")) || !value.isBefore(Instant.parse("+10000-01-01T00:00:00Z")) || value.getNano() % 1000 != 0))
            throw new IllegalArgumentException("UTC years 1000–9999 and microsecond precision required");
    }
    private AnnouncementRepository.Row row(long id, boolean lock) {
        if (id < 1) throw new IllegalArgumentException("Positive announcement ID required");
        return repository.find(id, lock).orElseThrow(AnnouncementService::missing);
    }
    private long actor() { Long actor = AuditContext.actorId(); if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account principal required"); return actor; }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private void enabled() { if (!flags.announcementsEnabled()) throw missing(); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Announcement not found"); }
}
