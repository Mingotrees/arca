package com.popman.arca.dto.v1.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

public record AuditEventResponse(long id, @JsonProperty("actor_id") @Schema(types = {"integer", "null"}, format = "int64") Long actorId, String action,
        @JsonProperty("target_type") String targetType, @JsonProperty("target_id") @Schema(types = {"integer", "null"}, format = "int64") Long targetId, String outcome,
        @JsonProperty("occurred_at") Instant occurredAt, @JsonProperty("correlation_id") String correlationId,
        Map<String, Object> metadata) {}
