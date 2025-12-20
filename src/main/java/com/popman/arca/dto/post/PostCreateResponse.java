package com.popman.arca.dto.post;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PostCreateResponse {
    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("post_id")
    private Integer postId;

    @JsonProperty("message")
    private String message;

    public PostCreateResponse() {}

    public PostCreateResponse(Long userId, Integer postId, String message) {
        this.userId = userId;
        this.postId = postId;
        this.message = message;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getPostId() {
        return postId;
    }

    public void setPostId(Integer postId) {
        this.postId = postId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
