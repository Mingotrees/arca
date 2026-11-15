package com.popman.arca.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "post_links")
public class PostLink {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    private Post post;
    @Column(nullable = false, length = 2048, updatable = false)
    private String url;
    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String normalizedUrl;
    @Column(nullable = false, length = 64, columnDefinition = "CHAR(64)", updatable = false)
    private String normalizedHash;
    @Column(length = 200, updatable = false)
    private String label;
    @Column(nullable = false, updatable = false)
    private int position;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected PostLink() {}
    public PostLink(String url, String normalizedUrl, String normalizedHash, String label, int position) {
        this.url = url;
        this.normalizedUrl = normalizedUrl;
        this.normalizedHash = normalizedHash;
        this.label = label;
        this.position = position;
    }
    @PrePersist
    private void initializeTimestamps() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        updatedAt = createdAt;
    }
    public PostLink copy() { return new PostLink(url, normalizedUrl, normalizedHash, label, position); }
    public void setPost(Post post) { this.post = post; }
    public String getUrl() { return url; }
    public String getNormalizedUrl() { return normalizedUrl; }
    public String getLabel() { return label; }
    public int getPosition() { return position; }
}
