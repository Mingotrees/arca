package com.popman.arca.service;


import com.popman.arca.dto.v1.post.PostApprovalRequest;
import com.popman.arca.dto.v1.post.PostCreateResponse;
import com.popman.arca.dto.v1.post.PostRequest;
import com.popman.arca.dto.v1.post.PostResponse;
import com.popman.arca.dto.v1.post.PostUpdateRequest;
import com.popman.arca.dto.v1.post.PostUpdateResponse;

import java.util.List;

public interface PostService {
    PostResponse getPostV1(Long rowId, Long actorId, boolean isAdmin);

    List<PostResponse> getAllPostsV1();

    List<PostResponse> getPostsBySubjectV1(Long subjectId);

    List<PostResponse> getMyPostsV1(Long actorId, String status);

    List<PostResponse> getAllUserPostV1(Long userId);

    List<PostResponse> getAllDepartmentPostV1(Long departmentId);

    PostCreateResponse createPostV1(PostRequest post, Long actorId);

    PostUpdateResponse updatePostV1(PostUpdateRequest updateRequest, Long rowId, Long actorId, boolean isAdmin);

    //fetch all that needs approval
    List<PostResponse> getAllPendingApprovalPostsV1();

    //approve post
    String approvePostV1(PostApprovalRequest postApprovalRequest, Long postId);

    //previous versions of a post
    List<PostResponse> getPostHistoryV1(Integer postId);

    void validateDeleteAccessV1(Long rowId, Long actorId, boolean isAdmin);

    //pending posts per department
    List<PostResponse> getPendingPostsByDepartmentV1(Long departmentId);
}
