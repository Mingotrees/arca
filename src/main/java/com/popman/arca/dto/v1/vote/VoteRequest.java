package com.popman.arca.dto.v1.vote;

public class VoteRequest {
    private Long postId;
    private String voteType; // "UPVOTE" or "DOWNVOTE"

    // Constructors
    public VoteRequest() {}

    public VoteRequest(Long postId, String voteType) {
        this.postId = postId;
        this.voteType = voteType;
    }

    // Getters and Setters
    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public String getVoteType() {
        return voteType;
    }

    public void setVoteType(String voteType) {
        this.voteType = voteType;
    }
}
