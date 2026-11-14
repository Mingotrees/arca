package com.popman.arca.dto.v1.vote;

public class VoteRequest {
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Positive
    @com.fasterxml.jackson.annotation.JsonProperty("post_id")
    private Long postId;
    @jakarta.validation.constraints.NotNull
    @jakarta.validation.constraints.Pattern(regexp = "UPVOTE|DOWNVOTE")
    @com.fasterxml.jackson.annotation.JsonProperty("vote_type")
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
