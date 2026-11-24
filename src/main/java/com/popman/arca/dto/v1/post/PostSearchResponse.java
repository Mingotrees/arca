package com.popman.arca.dto.v1.post;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PostSearchResponse(List<PostResponse> content, int page, int size,
                                 @JsonProperty("total_elements") long totalElements,
                                 @JsonProperty("total_pages") long totalPages, String sort) {}
