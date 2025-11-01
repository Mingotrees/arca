package com.popman.arca.service;


import com.popman.arca.dto.Post.PostApprovalRequest;
import com.popman.arca.dto.Post.PostRequest;
import com.popman.arca.dto.Post.PostResponse;
import com.popman.arca.dto.Post.PostUpdateRequest;
import com.popman.arca.entity.Post;

import java.util.List;

public interface PostService {
    //retrieve specific post
    public PostResponse getPost(Long postId);

    //retrieve all posts made by a specific user
    public List<PostResponse> getAllUserPost(Long userId);

    //retrieve all posts associated with a specific department
    public List<PostResponse> getAllDepartmentPost(Long departmentId);

    //create post
    public String createPost(PostRequest post);

    //update
    public String updatePost(PostUpdateRequest updateRequest, Long postId);

    //fetch all that needs approval
    public List<PostResponse> getAllPendingApprovalPosts();

    //approve post
    public String approvePost(PostApprovalRequest postApprovalRequest, Long postId);

    //previous versions of a post
    public List<PostResponse> getPostHistory(Integer postId);

    //softDelete
    public String deletePost(Long postId);

    //pending posts per department
    public List<PostResponse> getPendingPostsByDepartment(Long departmentId);
}
