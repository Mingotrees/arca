package com.popman.arca.controller;

import com.popman.arca.dto.vote.VoteRequest;
import com.popman.arca.dto.vote.VoteResponse;
import com.popman.arca.service.VoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/votes")
public class VoteController {

    private final VoteService voteService;

    public VoteController(VoteService voteService) {
        this.voteService = voteService;
    }

    @PostMapping
    public ResponseEntity<VoteResponse> vote(@RequestBody VoteRequest request,
                                                @RequestParam Long userId) {
        VoteResponse response = voteService.createOrUpdateVote(request, userId);

        if (response == null) {
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> removeVote(@PathVariable Long postId,
                                           @RequestParam Long userId) {
        voteService.removeVote(postId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{postId}/user/{userId}")
    public ResponseEntity<VoteResponse> getUserVote(@PathVariable Long postId,
                                                       @PathVariable Long userId) {
        VoteResponse vote = voteService.getUserVoteForPost(postId, userId);
        return ResponseEntity.ok(vote);
    }

    @GetMapping("/{postId}/upvotes")
    public ResponseEntity<Integer> getUpvoteCount(@PathVariable Long postId) {
        return ResponseEntity.ok(voteService.getUpvoteCount(postId));
    }

    @GetMapping("/{postId}/downvotes")
    public ResponseEntity<Integer> getDownvoteCount(@PathVariable Long postId) {
        return ResponseEntity.ok(voteService.getDownvoteCount(postId));
    }
}