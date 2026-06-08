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
import org.springframework.dao.DataIntegrityViolationException;

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


        // Attempt to find existing vote; if none, create new and save. Handle concurrent inserts.
        Vote vote = voteRepository.findByPostIdAndUserId(request.getPostId(), userId).orElse(null);

        try {
            if (vote != null) {
                if (request.getVoteType() != null && request.getVoteType().equals(vote.getVoteType())) {
                    voteRepository.delete(vote);
                    return null;
                }
                vote.setVoteType(request.getVoteType());
                Vote savedVote = voteRepository.saveAndFlush(vote);
                return convertToDTO(savedVote);
            } else {
                Vote newVote = new Vote();
                newVote.setPost(post);
                newVote.setUser(user);
                newVote.setVoteType(request.getVoteType());
                Vote savedVote = voteRepository.saveAndFlush(newVote);
                return convertToDTO(savedVote);
            }
        } catch (DataIntegrityViolationException ex) {
            // Concurrent insert occurred; reload existing vote and return its state or apply desired behavior
            Vote existing = voteRepository.findByPostIdAndUserId(request.getPostId(), userId)
                    .orElseThrow(() -> new RuntimeException("Vote conflict occurred and could not be resolved"));
            if (request.getVoteType() != null && request.getVoteType().equals(existing.getVoteType())) {
                // requested a toggle to same type - remove
                voteRepository.delete(existing);
                return null;
            }
            existing.setVoteType(request.getVoteType());
            Vote saved = voteRepository.save(existing);
            return convertToDTO(saved);
        }
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
        Long count = voteRepository.countByPostIdAndVoteType(postId, "UPVOTE");
        return count == null ? 0 : count.intValue();
    }

    public Integer getDownvoteCountV1(Long postId) {
        Long count = voteRepository.countByPostIdAndVoteType(postId, "DOWNVOTE");
        return count == null ? 0 : count.intValue();
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