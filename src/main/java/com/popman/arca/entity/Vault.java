package com.popman.arca.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vaults", uniqueConstraints = {
        @UniqueConstraint(name = "uk_vault_user_id", columnNames = {"user_id"})
}, indexes = {
        @Index(name = "idx_vault_user_id", columnList = "user_id")
})
public class Vault {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @JsonIgnore
    private User user;

    @OneToMany(mappedBy = "vault", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VaultItem> savedPosts = new ArrayList<>();

    public Vault() {
    }

    public Vault(User user) {
        this.user = user;
    }

    public void addSavedPost(VaultItem vaultItem) {
        savedPosts.add(vaultItem);
        vaultItem.setVault(this);
    }

    public void removeSavedPost(VaultItem vaultItem) {
        savedPosts.remove(vaultItem);
        vaultItem.setVault(null);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<VaultItem> getSavedPosts() {
        return savedPosts;
    }
}
