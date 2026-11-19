package com.popman.arca.dto.v1.post;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record TrendingResponse(@JsonProperty("calculated_at") Instant calculatedAt, String window, List<TrendingPostResponse> content) {}
