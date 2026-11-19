package com.popman.arca.dto.v1.announcement;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;

public record AnnouncementRequest(
        @NotBlank @Size(max = 128) String title,
        @Size(max = 4096) @Schema(types = {"string", "null"}, maxLength = 4096) String body,
        @Size(max = 2048) @Schema(types = {"string", "null"}, maxLength = 2048) String url,
        @Min(0) @Max(1000) @Schema(types = {"integer", "null"}, format = "int32") Integer priority,
        @Schema(types = {"boolean", "null"}) Boolean active,
        @JsonProperty("starts_at") @Schema(types = {"string", "null"}, format = "date-time") Instant startsAt,
        @JsonProperty("ends_at") @Schema(types = {"string", "null"}, format = "date-time") Instant endsAt) {}
