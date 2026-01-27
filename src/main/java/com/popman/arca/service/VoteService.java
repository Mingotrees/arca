package com.popman.arca.service;

import com.popman.arca.dto.v1.vote.VoteRequest;
import com.popman.arca.dto.v1.vote.VoteResponse;

public interface VoteService {

    VoteResponse createOrUpdateVoteV1(VoteRequest request, Long userId);

    void removeVoteV1(Long postId, Long userId);

    VoteResponse getUserVoteForPostV1(Long postId, Long userId);

    Integer getUpvoteCountV1(Long postId);

    Integer getDownvoteCountV1(Long postId);

}