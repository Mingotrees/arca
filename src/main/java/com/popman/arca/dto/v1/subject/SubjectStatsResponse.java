package com.popman.arca.dto.v1.subject;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record SubjectStatsResponse(
        @JsonProperty("approved_post_count") @Schema(description = "Distinct logical posts with approved/latest/non-deleted version rows linked to this subject", minimum = "0") long approvedPostCount,
        @JsonProperty("last_approved_post_at") @Schema(nullable = true, description = "Latest eligible Post.updated_at in its existing stored clock; null when empty") LocalDateTime lastApprovedPostAt) {}
