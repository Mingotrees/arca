package com.popman.arca.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

@Entity
@Table(name = "vault_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_vault_item_vault_post", columnNames = {"vault_id", "post_id"})
}, indexes = {
        @Index(name = "idx_vault_item_vault_id", columnList = "vault_id"),
        @Index(name = "idx_vault_item_post_id", columnList = "post_id")
})
public class VaultItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vault_id", nullable = false)
    @JsonIgnore
    private Vault vault;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    @JsonIgnoreProperties({"user", "department", "subjects", "files"})
    private Post post;

    @Column(columnDefinition = "text")
    private String label;

    public VaultItem() {
    }

    public VaultItem(Vault vault, Post post, String label) {
        this.vault = vault;
        this.post = post;
        this.label = label;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Vault getVault() {
        return vault;
    }

    public void setVault(Vault vault) {
        this.vault = vault;
    }

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}


