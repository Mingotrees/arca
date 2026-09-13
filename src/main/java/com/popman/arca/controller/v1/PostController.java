package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.common.MessageResponse;
import com.popman.arca.dto.v1.post.PostApprovalRequest;
import com.popman.arca.dto.v1.post.PostRequest;
import com.popman.arca.dto.v1.post.PostCreateResponse;
import com.popman.arca.dto.v1.post.PostResponse;
import com.popman.arca.dto.v1.post.PostUpdateRequest;
import com.popman.arca.dto.v1.post.PostUpdateResponse;
import com.popman.arca.entity.UserPrincipal;
import com.popman.arca.service.PostService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public ResponseEntity<List<PostResponse>> getAllPosts() {
        return ResponseEntity.ok(postService.getAllPostsV1());
    }

    @GetMapping("/subject/{subjectId}")
    public ResponseEntity<List<PostResponse>> getPostsBySubject(@PathVariable Long subjectId) {
        return ResponseEntity.ok(postService.getPostsBySubjectV1(subjectId));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<PostResponse>> getMyPosts(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        return ResponseEntity.ok(postService.getMyPostsV1(userPrincipal.getId(), status));
    }

    @GetMapping("/{rowId}")
    public ResponseEntity<PostResponse> getPost(
            @PathVariable Long rowId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        PostResponse response = postService.getPostV1(rowId, userPrincipal.getId(), isAdmin(userPrincipal));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PostResponse>> getAllUserPost(@PathVariable("userId") Long userId) {
        List<PostResponse> posts = postService.getAllUserPostV1(userId);
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<PostResponse>> getAllDepartmentPost(@PathVariable("departmentId") Long departmentId) {
        List<PostResponse> posts = postService.getAllDepartmentPostV1(departmentId);
        return ResponseEntity.ok(posts);
    }

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Post created",
            content = @Content(schema = @Schema(implementation = PostCreateResponse.class)))
    public ResponseEntity<PostCreateResponse> createPost(
            @Valid @RequestBody PostRequest newPost,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        PostCreateResponse response = postService.createPostV1(newPost, userPrincipal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{rowId}")
    public ResponseEntity<PostUpdateResponse> updatePost(
            @Valid @RequestBody PostUpdateRequest updatedPost,
            @PathVariable Long rowId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        PostUpdateResponse response = postService.updatePostV1(
                updatedPost, rowId, userPrincipal.getId(), isAdmin(userPrincipal));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{rowId}")
    @ApiResponse(responseCode = "501", description = "Post deletion is not implemented", content = @Content)
    public ResponseEntity<Void> deletePost(
            @PathVariable Long rowId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        postService.validateDeleteAccessV1(rowId, userPrincipal.getId(), isAdmin(userPrincipal));
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }


    @GetMapping("/pending")
    public ResponseEntity<List<PostResponse>> getAllPendingApprovalPosts() {
        List<PostResponse> pendingPosts = postService.getAllPendingApprovalPostsV1();
        return ResponseEntity.ok(pendingPosts);
    }

    @PostMapping("/{postId}/approve")
    @ApiResponse(responseCode = "200", description = "Post moderation completed",
            content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    public ResponseEntity<MessageResponse> approvePost(
            @Valid @RequestBody PostApprovalRequest approvalRequest,
            @PathVariable("postId") Long postId) {
        String message = postService.approvePostV1(approvalRequest, postId);
        return ResponseEntity.ok(new MessageResponse(message));
    }

    @GetMapping("/pending/department/{departmentId}")
    public ResponseEntity<List<PostResponse>> getPendingPostsByDepartment(@PathVariable Long departmentId){
        List<PostResponse> posts = postService.getPendingPostsByDepartmentV1(departmentId);
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/history/{postId}")
    public ResponseEntity<List<PostResponse>> getPostHistory(@PathVariable("postId") Integer postId) {
        List<PostResponse> versions = postService.getPostHistoryV1(postId);
        return ResponseEntity.ok(versions);
    }
    private boolean isAdmin(UserPrincipal userPrincipal) {
        return userPrincipal.hasRole("ROLE_ADMIN");
    }
}
