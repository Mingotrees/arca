package com.popman.arca.service;


import com.popman.arca.dto.v1.post.PostApprovalRequest;
import com.popman.arca.dto.v1.post.PostRequest;
import com.popman.arca.dto.v1.post.PostResponse;
import com.popman.arca.dto.v1.post.PostUpdateRequest;

import java.util.List;
import com.popman.arca.dto.v1.post.PostCreateResponse;

public interface PostService {
    //retrieve specific post
    public PostResponse getPostV1(Long postId);

    //retrieve all posts made by a specific user
    public List<PostResponse> getAllUserPostV1(Long userId);

    //retrieve all posts associated with a specific department
    public List<PostResponse> getAllDepartmentPostV1(Long departmentId);

    //create post
    public PostCreateResponse createPostV1(PostRequest post);

    //update
    public String updatePostV1(PostUpdateRequest updateRequest, Long postId);

    //fetch all that needs approval
    public List<PostResponse> getAllPendingApprovalPostsV1();

    //approve post
    public String approvePostV1(PostApprovalRequest postApprovalRequest, Long postId);

    //previous versions of a post
    public List<PostResponse> getPostHistoryV1(Integer postId);

    //softDelete
    public String deletePostV1(Long postId);

    //pending posts per department
    public List<PostResponse> getPendingPostsByDepartmentV1(Long departmentId);
}
