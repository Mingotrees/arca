package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.vote.VoteRequest;
import com.popman.arca.dto.v1.vote.VoteResponse;
import com.popman.arca.entity.UserPrincipal;
import com.popman.arca.service.VoteService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/votes")
@ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
@ApiResponse(responseCode = "400", description = "Invalid request or vote on a visible non-approved post", content = @Content)
public class VoteController {

    private final VoteService voteService;

    public VoteController(VoteService voteService) {
        this.voteService = voteService;
    }

    @PostMapping
    @Operation(description = "Request post_id is a post version database row id, not logical post_id. "
            + "All operations require parent-post visibility; new votes require APPROVED status even for owner/admin.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vote saved",
                    content = @Content(schema = @Schema(implementation = VoteResponse.class))),
            @ApiResponse(responseCode = "204", description = "Existing vote removed", content = @Content),
            @ApiResponse(responseCode = "404", description = "Post not found, including deleted posts", content = @Content)
    })
    public ResponseEntity<VoteResponse> vote(
            @jakarta.validation.Valid @RequestBody VoteRequest request,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Long userId = userPrincipal.getId();
        VoteResponse response = voteService.createOrUpdateVoteV1(request, userId);

        if (response == null) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{postId}")
    @ApiResponse(responseCode = "204", description = "Vote removed", content = @Content)
    @ApiResponse(responseCode = "404", description = "Post not found, including deleted posts", content = @Content)
    public ResponseEntity<Void> removeVote(
            @Parameter(description = "Post version database row id, not logical post_id") @PathVariable Long postId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Long userId = userPrincipal.getId();
        voteService.removeVoteV1(postId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{postId}/my-vote")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current vote",
                    content = @Content(schema = @Schema(implementation = VoteResponse.class))),
            @ApiResponse(responseCode = "204", description = "No current vote", content = @Content),
            @ApiResponse(responseCode = "404", description = "Post not found, including deleted posts", content = @Content)
    })
    public ResponseEntity<VoteResponse> getMyVote(
            @Parameter(description = "Post version database row id, not logical post_id") @PathVariable Long postId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {

        Long userId = userPrincipal.getId();
        VoteResponse vote = voteService.getUserVoteForPostV1(postId, userId);
        if (vote == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(vote);
    }

    @GetMapping("/{postId}/upvotes")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(type = "integer", format = "int32")))
    @ApiResponse(responseCode = "404", description = "Post not found, including deleted posts", content = @Content)
    public ResponseEntity<Integer> getUpvoteCount(
            @Parameter(description = "Post version database row id, not logical post_id") @PathVariable Long postId) {
        return ResponseEntity.ok(voteService.getUpvoteCountV1(postId));
    }

    @GetMapping("/{postId}/downvotes")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(type = "integer", format = "int32")))
    @ApiResponse(responseCode = "404", description = "Post not found, including deleted posts", content = @Content)
    public ResponseEntity<Integer> getDownvoteCount(
            @Parameter(description = "Post version database row id, not logical post_id") @PathVariable Long postId) {
        return ResponseEntity.ok(voteService.getDownvoteCountV1(postId));
    }
}
