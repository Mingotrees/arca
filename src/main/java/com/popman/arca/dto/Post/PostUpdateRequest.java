package com.popman.arca.dto.Post;

import jakarta.validation.constraints.Size;

import java.util.List;

public class PostUpdateRequest {
    @Size(max = 30, message = "Title must not exceed 30 characters")
    private String title;

    private String content;

    private List<String> postTags;

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

    public List<String> getPostTags() {
        return postTags;
    }

    public void setPostTags(List<String> postTags) {
        this.postTags = postTags;
    }
}
