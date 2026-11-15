package com.popman.arca.dto.v1.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserStatsResponse(
        @JsonProperty("approved_post_count") @Schema(description = "Distinct logical posts with approved/latest/non-deleted version rows", minimum = "0") long approvedPostCount,
        @JsonProperty("upvotes_received") @Schema(description = "Stored upvotes on eligible version rows", minimum = "0") long upvotesReceived,
        @JsonProperty("downvotes_received") @Schema(description = "Stored downvotes on eligible version rows", minimum = "0") long downvotesReceived) {}
