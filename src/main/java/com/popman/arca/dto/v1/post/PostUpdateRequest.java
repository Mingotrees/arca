package com.popman.arca.dto.v1.post;

import jakarta.validation.constraints.Size;

public class PostUpdateRequest {
    @Size(max = 30, message = "Title must not exceed 30 characters")
    private String title;

    private String content;

    @com.fasterxml.jackson.annotation.JsonProperty("post_tag")
    private String postTag;

    @io.swagger.v3.oas.annotations.media.Schema(types = {"array", "null"}, description = "Ordered HTTP(S) links, maximum 10. Omitted/null copies source version links; [] clears only the new version; canonical duplicates reject the request.")
    @jakarta.validation.constraints.Size(max = 10)
    private java.util.List<PostLinkRequest> links;
    public java.util.List<PostLinkRequest> getLinks() { return links; }
    public void setLinks(java.util.List<PostLinkRequest> links) { this.links = links; }

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

    public String getPostTag() {
        return postTag;
    }

    public void setPostTag(String postTag) {
        this.postTag = postTag;
    }
}
