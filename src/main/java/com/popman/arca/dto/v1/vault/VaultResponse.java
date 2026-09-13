package com.popman.arca.dto.v1.vault;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.popman.arca.entity.Vault;

public class VaultResponse {
    private final Long id;

    @JsonProperty("post_id")
    private final Long postId;

    private final String label;

    public VaultResponse(Vault vault) {
        this.id = vault.getId();
        this.postId = vault.getPost().getId();
        this.label = vault.getLabel();
    }

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public String getLabel() {
        return label;
    }
}
