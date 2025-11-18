package com.popman.arca.service;

import com.popman.arca.dto.vote.VoteRequest;
import com.popman.arca.dto.vote.VoteResponse;

public interface VoteService {

    VoteResponse createOrUpdateVote(VoteRequest request, Long userId);

    void removeVote(Long postId, Long userId);

    VoteResponse getUserVoteForPost(Long postId, Long userId);

    Integer getUpvoteCount(Long postId);

    Integer getDownvoteCount(Long postId);

}