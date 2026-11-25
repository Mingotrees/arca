package com.popman.arca.dto.v1.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PostRequest {
    @NotBlank(message = "Title is required")
    @Size(max = 30, message = "Title must not exceed 30 characters")
    private String title;

    @NotBlank(message = "Content is required")
    @io.swagger.v3.oas.annotations.media.Schema(description = "Post body HTML fragment. Allowed tags: p, br, h1, h2, h3, b, strong, i, em, u, s, code, blockquote, ol, ul, li. "
            + "Only integer ol start and li value attributes are kept. Unsupported markup is stripped; use links for clickable URLs. "
            + "After sanitization, content must contain text and fit in 65535 UTF-8 bytes.")
    private String content;

    @io.swagger.v3.oas.annotations.media.Schema(deprecated = true,
            description = "Ignored legacy ownership input; owner is always the authenticated JWT principal")
    @com.fasterxml.jackson.annotation.JsonProperty("user_id")
    private Long userId;

    @NotNull(message = "Department ID is required")
    @com.fasterxml.jackson.annotation.JsonProperty("department_id")
    private Long departmentId;

    @com.fasterxml.jackson.annotation.JsonProperty("post_tag")
    private String postTag;

    @io.swagger.v3.oas.annotations.media.Schema(types = {"array", "null"}, description = "Optional ordered HTTP(S) links, maximum 10. Omitted/null creates no links; canonical duplicates reject the request.")
    @jakarta.validation.constraints.Size(max = 10)
    private java.util.List<PostLinkRequest> links;
    public java.util.List<PostLinkRequest> getLinks() { return links; }
    public void setLinks(java.util.List<PostLinkRequest> links) { this.links = links; }

    public PostRequest() {}

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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public String getPostTag() {
        return postTag;
    }

    public void setPostTag(String postTag) {
        this.postTag = postTag;
    }
}
