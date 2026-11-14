
package com.popman.arca.dto.v1.vault;

public class VaultRequest {

    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Positive
    @com.fasterxml.jackson.annotation.JsonProperty("post_id")
    private Long postId;
    @jakarta.validation.constraints.Size(max = 255)
    private String label;

    public VaultRequest() {
    }

    public VaultRequest(Long postId, String label) {
        this.postId = postId;
        this.label = label;
    }


    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
