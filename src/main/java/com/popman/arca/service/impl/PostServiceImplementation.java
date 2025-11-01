package com.popman.arca.service.impl;


import com.popman.arca.dto.Post.PostApprovalRequest;
import com.popman.arca.dto.Post.PostRequest;
import com.popman.arca.dto.Post.PostResponse;
import com.popman.arca.dto.Post.PostUpdateRequest;
import com.popman.arca.entity.Department;
import com.popman.arca.entity.Post;
import com.popman.arca.entity.User;
import com.popman.arca.repository.DepartmentRepository;
import com.popman.arca.repository.PostRepository;
import com.popman.arca.repository.UserRepository;
import com.popman.arca.service.PostService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PostServiceImplementation implements PostService {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Override
    public PostResponse getPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(()-> new RuntimeException("Post not found with id: " + postId));
        return mapToResponse(post);
    }

    @Override
    public List<PostResponse> getAllUserPost(Long userId) {
        List<Post> posts = postRepository.findLatestApprovedPostsByDepartmentId(userId);
        return posts.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<PostResponse> getAllDepartmentPost(Long departmentId) {
        List<Post> posts = postRepository.findLatestApprovedPostsByDepartmentId(departmentId);
        return posts.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public String createPost(PostRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException ("User not found with Id " + request.getUserId()));

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(()-> new RuntimeException("Department not found with id " + request.getDepartmentId()));

        Integer nextPostId = postRepository.getNextPostId();

        Post post = new Post();
        post.setPost_id(nextPostId);
        post.setContent(request.getContent());
        post.setUserId(request.getUserId());
        post.setTitle(request.getTitle());
        post.setVersion(1);
        post.setDepartmentId(request.getDepartmentId());
        post.setIsLatestVersion(true);
        post.setStatus("PENDING_APPROVAL");
        post.setUpdatedAt(LocalDateTime.now());
        post.setCreatedAt(LocalDateTime.now());

        postRepository.save(post);

        return "Post created successfully with Id " + post.getId() + ". Awaiting admin approval";
    }

    @Override
    @Transactional
    public String updatePost(PostUpdateRequest updateRequest, Long postId) {
        // Find the current post
        Post currentPost = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + postId));

        // Validate that the post is approved (only approved posts can be updated)
        if (!"APPROVED".equals(currentPost.getStatus())) {
            throw new RuntimeException("Only approved posts can be updated. Current status: " + currentPost.getStatus());
        }

        // Get the maximum version for this post_id
        Integer maxVersion = postRepository.findMaxVersionByPostId(currentPost.getPost_id());

        // Mark all versions of this post as not latest
        postRepository.updateIsLatestVersionByPostId(currentPost.getPost_id(), false);

        // Create new version
        Post newVersion = new Post();
        newVersion.setPost_id(currentPost.getPost_id()); // Same post_id group
        newVersion.setVersion(maxVersion + 1); // Increment version
        newVersion.setTitle(updateRequest.getTitle() != null ? updateRequest.getTitle() : currentPost.getTitle());
        newVersion.setContent(updateRequest.getContent() != null ? updateRequest.getContent() : currentPost.getContent());
        newVersion.setStatus("PENDING_APPROVAL"); // Requires admin approval
        newVersion.setUserId(currentPost.getUserId());
        newVersion.setDepartmentId(currentPost.getDepartmentId());
        newVersion.setIsLatestVersion(true);
        newVersion.setCreatedAt(LocalDateTime.now());
        newVersion.setUpdatedAt(LocalDateTime.now());

        postRepository.save(newVersion);

//        // TODO: Handle postTags update if provided
//        if (updateRequest.getPostTags() != null) {
//            // Update post tags logic here
//        }

        return "Post updated successfully. Version " + newVersion.getVersion() + " is pending approval.";
    }

    @Override
    public List<PostResponse> getAllPendingApprovalPosts() {
        List<Post> pendingPost = postRepository.findAllPendingApprovalPosts();
        return pendingPost.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public String approvePost(PostApprovalRequest approvalRequest, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + postId));

        // Validate that the post is pending approval
        if (!"PENDING_APPROVAL".equals(post.getStatus())) {
            throw new RuntimeException("Post is not pending approval. Current status: " + post.getStatus());
        }

        if (approvalRequest.getApproved()) {
            // Approve the post
            post.setStatus("APPROVED");

            // If this is a version > 1 (an update), handle the previous approved version
            if (post.getVersion() > 1) {
                Post previousApproved = postRepository
                        .findPreviousApprovedVersion(post.getPost_id(), post.getVersion());

                if (previousApproved != null) {
                    previousApproved.setStatus("SUPERSEDED");
                    previousApproved.setIsLatestVersion(false);
                    postRepository.save(previousApproved);
                }
            }

            post.setUpdatedAt(LocalDateTime.now());
            postRepository.save(post);

            return "Post approved successfully. Now visible to users.";

        } else {
            // Reject the post
            post.setStatus("REJECTED");
            post.setIsLatestVersion(false);

            if (approvalRequest.getRejectionReason() != null) {
                post.setRejectionReason(approvalRequest.getRejectionReason());
            }

            // If rejecting an update, restore the previous approved version as latest
            if (post.getVersion() > 1) {
                Post previousApproved = postRepository
                        .findPreviousApprovedVersion(post.getPost_id(), post.getVersion());

                if (previousApproved != null) {
                    previousApproved.setIsLatestVersion(true);
                    postRepository.save(previousApproved);
                }
            }

            post.setUpdatedAt(LocalDateTime.now());
            postRepository.save(post);

            return "Post rejected. " +
                    (approvalRequest.getRejectionReason() != null ?
                            "Reason: " + approvalRequest.getRejectionReason() : "No reason given");
        }
    }

    @Override
    public List<PostResponse> getPostHistory(Integer postId) {
        List<Post> versions = postRepository.findAllVersionsByPostId(postId);
        return versions.stream().map(this::mapToResponse).toList();
    }

    @Override
    //softdelete to be implemented
    public String deletePost(Long postId) {
        return "";
    }

    private PostResponse mapToResponse(Post post) {
        PostResponse response = new PostResponse();
        response.setId(post.getId());
        response.setPostId(post.getPost_id());
        response.setVersion(post.getVersion());
        response.setTitle(post.getTitle());
        response.setContent(post.getContent());
        response.setStatus(post.getStatus());
        response.setIsLatestVersion(post.getIsLatestVersion());
        response.setCreatedAt(post.getCreatedAt());
        response.setUpdatedAt(post.getUpdatedAt());

        if (post.getUser() != null) {
            response.setUserId(post.getUser().getId());
            response.setFirstName(post.getUser().getFirstName());
            response.setLastName(post.getUser().getLastName());// Adjust based on your User entity
        } else {
            response.setUserId(post.getUserId());
        }

        if (post.getDepartment() != null) {
            response.setDepartmentId(post.getDepartment().getId());
            response.setDepartmentName(post.getDepartment().getName()); // Adjust based on your Department entity
        } else {
            response.setDepartmentId(post.getDepartmentId());
        }

        if (post.getRejectionReason() != null) {
            response.setRejectionReason(post.getRejectionReason());
        }

        return response;
    }


}
