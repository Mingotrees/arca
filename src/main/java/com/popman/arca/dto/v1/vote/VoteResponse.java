package com.popman.arca.dto.v1.vote;

public class VoteResponse {
    private Long id;
    private Long postId;
    private Long userId;
    private String voteType;

    // Constructors
    public VoteResponse() {}

    public VoteResponse(Long id, Long postId, Long userId, String voteType) {
        this.id = id;
        this.postId = postId;
        this.userId = userId;
        this.voteType = voteType;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getVoteType() {
        return voteType;
    }

    public void setVoteType(String voteType) {
        this.voteType = voteType;
    }
}
