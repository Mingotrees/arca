package com.popman.arca.dto.v1.post;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;

public record PostSearchCriteria(String q, Long subjectId, Long departmentId, Long authorId,
                                 LocalDateTime from, LocalDateTime to, int page, int size,
                                 String sort, String status) {
    public PostSearchCriteria {
        q = q == null || q.isBlank() ? null : q.strip();
        if (q != null && q.length() > 200) throw new IllegalArgumentException("q must not exceed 200 characters");
        for (Long id : new Long[]{subjectId, departmentId, authorId}) {
            if (id != null && id <= 0) throw new IllegalArgumentException("Filter IDs must be positive");
        }
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
        if (from != null && (from.getYear() < 1000 || from.getYear() > 9999)
                || to != null && (to.getYear() < 1000 || to.getYear() > 9999)) {
            throw new IllegalArgumentException("Date filters must be within MySQL DATETIME range");
        }
        if (from != null && to != null && !from.isBefore(to)) throw new IllegalArgumentException("from must precede to");
        if (from != null && from.getNano() % 1000 != 0 || to != null && to.getNano() % 1000 != 0) {
            throw new IllegalArgumentException("Date filters support microsecond precision only");
        }
        sort = sort == null ? (q == null ? "newest" : "relevance") : sort.strip().toLowerCase(Locale.ROOT);
        if (!Set.of("newest", "oldest", "relevance", "vote_score").contains(sort)) throw new IllegalArgumentException("Unsupported sort");
        if (sort.equals("relevance") && q == null) throw new IllegalArgumentException("relevance requires a nonblank q");
        if (status != null) {
            status = status.strip().toUpperCase(Locale.ROOT);
            if (!Set.of("APPROVED", "PENDING_APPROVAL", "REJECTED", "SUPERSEDED").contains(status)) {
                throw new IllegalArgumentException("Unsupported status");
            }
        }
    }

    public static LocalDateTime parseDate(String raw) {
        if (raw == null) return null;
        try { return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME); }
        catch (DateTimeParseException ex) { throw new IllegalArgumentException("Date filters must be ISO local date-times without an offset"); }
    }
}
