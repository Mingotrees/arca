package com.popman.arca.dto.v1.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AuditEventPage(List<AuditEventResponse> content, int page, int size,
        @JsonProperty("total_elements") long totalElements, @JsonProperty("total_pages") long totalPages) {}
