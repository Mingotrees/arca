package com.popman.arca.dto.v1.post;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record TrendingPostResponse(long id, @JsonProperty("post_id") int postId, String title,
        @JsonProperty("user_id") long userId, @JsonProperty("department_id") long departmentId,
        @JsonProperty("approved_at") Instant approvedAt,
        @JsonProperty("recent_upvotes") long recentUpvotes, @JsonProperty("recent_downvotes") long recentDownvotes) {}
