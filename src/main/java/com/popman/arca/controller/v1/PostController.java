package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.post.PostApprovalRequest;
import com.popman.arca.dto.v1.post.PostRequest;
import com.popman.arca.dto.v1.post.PostCreateResponse;
import com.popman.arca.dto.v1.post.PostResponse;
import com.popman.arca.dto.v1.post.PostUpdateRequest;
import com.popman.arca.service.PostService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    // === User Operations ===

    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPost(@PathVariable("postId") Long postId) {
        PostResponse response = postService.getPostV1(postId);
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
    public ResponseEntity<PostCreateResponse> createPost(@Valid @RequestBody PostRequest newPost) {
        PostCreateResponse response = postService.createPostV1(newPost);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{postId}")
    public ResponseEntity<String> updatePost(
            @Valid @RequestBody PostUpdateRequest updatedPost,
            @PathVariable("postId") Long postId) {
        String message = postService.updatePostV1(updatedPost, postId);
        return ResponseEntity.ok(message);
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<String> deletePost(@PathVariable("postId") Long postId) {
        String message = postService.deletePostV1(postId);
        return ResponseEntity.ok(message);
    }


    @GetMapping("/pending")
    public ResponseEntity<List<PostResponse>> getAllPendingApprovalPosts() {
        List<PostResponse> pendingPosts = postService.getAllPendingApprovalPostsV1();
        return ResponseEntity.ok(pendingPosts);
    }

    @PostMapping("/{postId}/approve")
    public ResponseEntity<String> approvePost(
            @Valid @RequestBody PostApprovalRequest approvalRequest,
            @PathVariable("postId") Long postId) {
        String message = postService.approvePostV1(approvalRequest, postId);
        return ResponseEntity.ok(message);
    }

    @GetMapping("/pending/department/{departmentId}")
    public ResponseEntity<List<PostResponse>> getPendingPostsByDepartment(@PathVariable Long departmentId){
        List<PostResponse> posts = postService.getPendingPostsByDepartmentV1(departmentId);
        return ResponseEntity.ok(posts);
    }

    // === Version History ===

    @GetMapping("/history/{postId}")
    public ResponseEntity<List<PostResponse>> getPostHistory(@PathVariable("postId") Integer postId) {
        List<PostResponse> versions = postService.getPostHistoryV1(postId);
        return ResponseEntity.ok(versions);
    }



//    @GetMapping("/latest/{postId}")
//    public ResponseEntity<PostResponse> getLatestVersion(@PathVariable("postId") Integer postId) {
//        PostResponse latestVersion = postService.getLatestVersion(postId);
//        return ResponseEntity.ok(latestVersion);
//    }
}

