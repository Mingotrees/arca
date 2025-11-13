package com.popman.arca.controller;

import com.popman.arca.dto.post.PostApprovalRequest;
import com.popman.arca.dto.post.PostRequest;
import com.popman.arca.dto.post.PostResponse;
import com.popman.arca.dto.post.PostUpdateRequest;
import com.popman.arca.service.PostService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    // === User Operations ===

    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPost(@PathVariable("postId") Long postId) {
        PostResponse response = postService.getPost(postId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PostResponse>> getAllUserPost(@PathVariable("userId") Long userId) {
        List<PostResponse> posts = postService.getAllUserPost(userId);
        return ResponseEntity.ok(posts);
    }

    @GetMapping("/department/{departmentId}")
    public ResponseEntity<List<PostResponse>> getAllDepartmentPost(@PathVariable("departmentId") Long departmentId) {
        List<PostResponse> posts = postService.getAllDepartmentPost(departmentId);
        return ResponseEntity.ok(posts);
    }

    @PostMapping
    public ResponseEntity<String> createPost(@Valid @RequestBody PostRequest newPost) {
        String message = postService.createPost(newPost);
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }

    @PutMapping("/{postId}")
    public ResponseEntity<String> updatePost(
            @Valid @RequestBody PostUpdateRequest updatedPost,
            @PathVariable("postId") Long postId) {
        String message = postService.updatePost(updatedPost, postId);
        return ResponseEntity.ok(message);
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<String> deletePost(@PathVariable("postId") Long postId) {
        String message = postService.deletePost(postId);
        return ResponseEntity.ok(message);
    }

    // === Admin Operations ===

    @GetMapping("/pending")
    public ResponseEntity<List<PostResponse>> getAllPendingApprovalPosts() {
        List<PostResponse> pendingPosts = postService.getAllPendingApprovalPosts();
        return ResponseEntity.ok(pendingPosts);
    }

    @PostMapping("/{postId}/approve")
    public ResponseEntity<String> approvePost(
            @Valid @RequestBody PostApprovalRequest approvalRequest,
            @PathVariable("postId") Long postId) {
        String message = postService.approvePost(approvalRequest, postId);
        return ResponseEntity.ok(message);
    }

    @GetMapping("/pending/department/{departmentId}")
    public ResponseEntity<List<PostResponse>> getPendingPostsByDepartment(@PathVariable Long departmentId){
        List<PostResponse> posts = postService.getPendingPostsByDepartment(departmentId);
        return ResponseEntity.ok(posts);
    }

    // === Version History ===

    @GetMapping("/history/{postId}")
    public ResponseEntity<List<PostResponse>> getPostHistory(@PathVariable("postId") Integer postId) {
        List<PostResponse> versions = postService.getPostHistory(postId);
        return ResponseEntity.ok(versions);
    }



//    @GetMapping("/latest/{postId}")
//    public ResponseEntity<PostResponse> getLatestVersion(@PathVariable("postId") Integer postId) {
//        PostResponse latestVersion = postService.getLatestVersion(postId);
//        return ResponseEntity.ok(latestVersion);
//    }
}

