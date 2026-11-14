package com.popman.arca.dto.v1.vault;

public class EditVaultLabelRequest {
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Positive
    @com.fasterxml.jackson.annotation.JsonProperty("post_id")
    private Long postId;
    @jakarta.validation.constraints.Size(max = 255)
    @com.fasterxml.jackson.annotation.JsonProperty("new_label")
    private String newLabel;

    public EditVaultLabelRequest() {
    }

    public EditVaultLabelRequest(Long postId, String newLabel) {
        this.postId = postId;
        this.newLabel = newLabel;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public String getNewLabel() {
        return newLabel;
    }

    public void setNewLabel(String newLabel) {
        this.newLabel = newLabel;
    }
}
