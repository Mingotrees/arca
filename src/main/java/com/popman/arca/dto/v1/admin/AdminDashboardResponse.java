package com.popman.arca.dto.v1.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

public record AdminDashboardResponse(@JsonProperty("calculated_at") Instant calculatedAt, String timezone, Instant from, Instant to,
                                     Users users, Posts posts, Moderation moderation, Engagement engagement) {
    public record Users(long active, long deleted, @JsonProperty("by_role") Map<String, Long> byRole) {}
    public record Posts(long active, long deleted, @JsonProperty("by_status") Map<String, Long> byStatus,
                        @JsonProperty("created_in_period") long createdInPeriod,
                        @JsonProperty("approved_versions_in_period") long approvedVersionsInPeriod,
                        @JsonProperty("legacy_approved_versions_without_date") long legacyApprovedVersionsWithoutDate) {}
    public record Moderation(long pending) {}
    public record Engagement(long attachments, @JsonProperty("attachment_bytes") long attachmentBytes,
                             @JsonProperty("attachments_without_known_size") long attachmentsWithoutKnownSize,
                             long upvotes, long downvotes, @JsonProperty("vault_saves") long vaultSaves) {}
}
