package com.popman.arca.dto.v1.announcement;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record AnnouncementResponse(long id, String title,
        @Schema(types = {"string", "null"}) String body, @Schema(types = {"string", "null"}) String url, int priority,
        @JsonProperty("starts_at") @Schema(types = {"string", "null"}, format = "date-time") Instant startsAt,
        @JsonProperty("ends_at") @Schema(types = {"string", "null"}, format = "date-time") Instant endsAt) {}
