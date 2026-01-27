package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.vote.VoteRequest;
import com.popman.arca.dto.v1.vote.VoteResponse;
import com.popman.arca.entity.UserPrincipal;
import com.popman.arca.service.VoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/votes")
public class VoteController {

    private final VoteService voteService;

    public VoteController(VoteService voteService) {
        this.voteService = voteService;
    }

    @PostMapping
    public ResponseEntity<VoteResponse> vote(
            @RequestBody VoteRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Long userId = userPrincipal.getId();
        VoteResponse response = voteService.createOrUpdateVoteV1(request, userId);

        if (response == null) {
            return ResponseEntity.ok().build();
        }

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> removeVote(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Long userId = userPrincipal.getId();
        voteService.removeVoteV1(postId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{postId}/my-vote")
    public ResponseEntity<VoteResponse> getMyVote(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Long userId = userPrincipal.getId();
        VoteResponse vote = voteService.getUserVoteForPostV1(postId, userId);
        return ResponseEntity.ok(vote);
    }

    @GetMapping("/{postId}/upvotes")
    public ResponseEntity<Integer> getUpvoteCount(@PathVariable Long postId) {
        return ResponseEntity.ok(voteService.getUpvoteCountV1(postId));
    }

    @GetMapping("/{postId}/downvotes")
    public ResponseEntity<Integer> getDownvoteCount(@PathVariable Long postId) {
        return ResponseEntity.ok(voteService.getDownvoteCountV1(postId));
    }
}