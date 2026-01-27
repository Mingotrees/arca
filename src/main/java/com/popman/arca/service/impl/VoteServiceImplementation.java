package com.popman.arca.service.impl;

import com.popman.arca.dto.v1.vote.VoteRequest;
import com.popman.arca.dto.v1.vote.VoteResponse;
import com.popman.arca.entity.Post;
import com.popman.arca.entity.User;
import com.popman.arca.entity.Vote;
import com.popman.arca.repository.PostRepository;
import com.popman.arca.repository.UserRepository;
import com.popman.arca.repository.VoteRepository;
import com.popman.arca.service.VoteService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VoteServiceImplementation implements VoteService {

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public VoteResponse createOrUpdateVoteV1(VoteRequest request, Long userId) {

        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new RuntimeException("Post not found"));


        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));


        Vote vote = voteRepository.findByPostIdAndUserId(request.getPostId(), userId)
                .orElse(new Vote());


        if (vote.getId() != null && vote.getVoteType().equals(request.getVoteType())) {
            voteRepository.delete(vote);
            return null;
        }

        // Create or update vote
        vote.setPost(post);
        vote.setUser(user);
        vote.setVoteType(request.getVoteType());

        Vote savedVote = voteRepository.save(vote);

        return convertToDTO(savedVote);
    }

    @Transactional
    public void removeVoteV1(Long postId, Long userId) {
        voteRepository.deleteByPostIdAndUserId(postId, userId);
    }

    public VoteResponse getUserVoteForPostV1(Long postId, Long userId) {
        return voteRepository.findByPostIdAndUserId(postId, userId)
                .map(this::convertToDTO)
                .orElse(null);
    }

    public Integer getUpvoteCountV1(Long postId) {
        return voteRepository.countByPostIdAndVoteType(postId, "UPVOTE");
    }

    public Integer getDownvoteCountV1(Long postId) {
        return voteRepository.countByPostIdAndVoteType(postId, "DOWNVOTE");
    }

    private VoteResponse convertToDTO(Vote vote) {
        return new VoteResponse(
                vote.getId(),
                vote.getPost().getId(),
                vote.getUser().getId(),
                vote.getVoteType()
        );
    }
}