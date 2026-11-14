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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
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
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PostResponse.class)))
    @ApiResponse(responseCode = "404", description = "Post not found, including deleted posts", content = @Content)
    public ResponseEntity<PostResponse> getPost(
            @Parameter(description = "Post version database row id, not logical post_id") @PathVariable Long rowId,
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
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PostUpdateResponse.class)))
    @ApiResponse(responseCode = "404", description = "Post not found, including deleted posts", content = @Content)
    public ResponseEntity<PostUpdateResponse> updatePost(
            @Valid @RequestBody PostUpdateRequest updatedPost,
            @Parameter(description = "Post version database row id, not logical post_id") @PathVariable Long rowId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        PostUpdateResponse response = postService.updatePostV1(
                updatedPost, rowId, userPrincipal.getId(), isAdmin(userPrincipal));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{rowId}")
    @Operation(summary = "Soft-delete a logical post", description = "Owner/admin soft-deletes every version sharing the logical post_id. " +
            "Rows and files are retained; repeated authorized deletion returns 204.",
            security = @SecurityRequirement(name = "bearerAuth"),
            parameters = @Parameter(name = "X-Request-ID", in = ParameterIn.HEADER,
                    description = "Optional correlation ID: 1-64 letters, digits, dots, underscores or hyphens; invalid values are replaced by a UUID",
                    schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "204", description = "Logical post deleted; no response body", content = @Content,
            headers = @Header(name = "X-Request-ID", description = "Safe request correlation ID", schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "401", description = "Authentication required",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"),
                    examples = @ExampleObject(value = "{\"error\":\"unauthorized\",\"message\":\"Authentication required\"}")))
    @ApiResponse(responseCode = "403", description = "Only owner/admin may delete an active post",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"),
                    examples = @ExampleObject(value = "{\"timestamp\":\"2026-11-14T09:00:00\",\"status\":403," +
                            "\"error\":\"ResponseStatusException\",\"message\":\"Only post owner or admin may modify this post\"}")))
    @ApiResponse(responseCode = "404", description = "Row missing or deleted post inaccessible to caller",
            content = @Content(mediaType = "application/json", schema = @Schema(type = "object"),
                    examples = @ExampleObject(value = "{\"timestamp\":\"2026-11-14T09:00:00\",\"status\":404," +
                            "\"error\":\"ResponseStatusException\",\"message\":\"Post not found\"}")))
    public ResponseEntity<Void> deletePost(
            @Parameter(description = "Database row id of any version, not logical post_id") @PathVariable Long rowId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        postService.deletePostV1(rowId, userPrincipal.getId(), isAdmin(userPrincipal));
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/pending")
    public ResponseEntity<List<PostResponse>> getAllPendingApprovalPosts() {
        List<PostResponse> pendingPosts = postService.getAllPendingApprovalPostsV1();
        return ResponseEntity.ok(pendingPosts);
    }

    @PostMapping("/{postId}/approve")
    @ApiResponse(responseCode = "200", description = "Post moderation completed",
            content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    @ApiResponse(responseCode = "404", description = "Post not found, including deleted posts", content = @Content)
    public ResponseEntity<MessageResponse> approvePost(
            @Valid @RequestBody PostApprovalRequest approvalRequest,
            @Parameter(description = "Post version database row id, not logical post_id") @PathVariable("postId") Long postId) {
        String message = postService.approvePostV1(approvalRequest, postId);
        return ResponseEntity.ok(new MessageResponse(message));
    }

    @GetMapping("/pending/department/{departmentId}")
    public ResponseEntity<List<PostResponse>> getPendingPostsByDepartment(@PathVariable Long departmentId){
        List<PostResponse> posts = postService.getPendingPostsByDepartmentV1(departmentId);
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/history/{postId}")
    @Operation(description = "Returns active versions for a logical post_id; deleted posts have an empty history list.")
    public ResponseEntity<List<PostResponse>> getPostHistory(
            @Parameter(description = "Logical post_id, not a version database row id") @PathVariable("postId") Integer postId) {
        List<PostResponse> versions = postService.getPostHistoryV1(postId);
        return ResponseEntity.ok(versions);
    }
    private boolean isAdmin(UserPrincipal userPrincipal) {
        return userPrincipal.hasRole("ROLE_ADMIN");
    }
}
