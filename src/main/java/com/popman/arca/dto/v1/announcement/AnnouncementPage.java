package com.popman.arca.dto.v1.announcement;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AnnouncementPage(List<AdminAnnouncementResponse> content, int page, int size,
        @JsonProperty("total_elements") long totalElements, @JsonProperty("total_pages") long totalPages) {}
