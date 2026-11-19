package com.popman.arca.dto.v1.announcement;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record AdminAnnouncementResponse(long id, String title,
        @Schema(types = {"string", "null"}) String body, @Schema(types = {"string", "null"}) String url,
        int priority, boolean active,
        @JsonProperty("starts_at") @Schema(types = {"string", "null"}, format = "date-time") Instant startsAt,
        @JsonProperty("ends_at") @Schema(types = {"string", "null"}, format = "date-time") Instant endsAt,
        @JsonProperty("created_by") long createdBy, @JsonProperty("updated_by") long updatedBy,
        @JsonProperty("created_at") Instant createdAt, @JsonProperty("updated_at") Instant updatedAt) {}
