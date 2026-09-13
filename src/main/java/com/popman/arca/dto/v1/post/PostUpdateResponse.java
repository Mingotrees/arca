package com.popman.arca.dto.v1.post;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PostUpdateResponse {
    private Long id;

    @JsonProperty("post_id")
    private Integer postId;

    private Integer version;
    private String status;
    private String message;

    public PostUpdateResponse() {}

    public PostUpdateResponse(Long id, Integer postId, Integer version, String status, String message) {
        this.id = id;
        this.postId = postId;
        this.version = version;
        this.status = status;
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getPostId() {
        return postId;
    }

    public void setPostId(Integer postId) {
        this.postId = postId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
