package com.popman.arca.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

@Entity
@Table(name = "vaults",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_vault_user_post", columnNames = {"user_id", "post_id"})
        },
        indexes = {
                @Index(name = "idx_vault_id", columnList = "id"),
                @Index(name = "idx_vault_user_id", columnList = "user_id"),
                @Index(name = "idx_vault_post_id", columnList = "post_id")
        })
public class Vault {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    @JsonIgnore
    private Post post;

    @Column(columnDefinition = "text")
    private String label;


    public Vault() {
    }

    public Vault(User user, Post post, String label) {
        this.user = user;
        this.post = post;
        this.label = label;
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
