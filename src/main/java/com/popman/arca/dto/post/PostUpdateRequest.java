package com.popman.arca.dto.post;

import jakarta.validation.constraints.Size;

import java.util.List;

public class PostUpdateRequest {
    @Size(max = 30, message = "Title must not exceed 30 characters")
    private String title;

    private String content;

    private List<Long> postTags;

    public PostUpdateRequest() {}

    // Getters and Setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<Long> getPostTags() {
        return postTags;
    }

    public void setPostTags(List<Long> postTags) {
        this.postTags = postTags;
    }
}
