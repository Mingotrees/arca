package com.popman.arca.service;


import com.popman.arca.entity.Post;

import java.util.List;

public interface PostService {
    //retrieve specific post
    public Post getPost(Long postId);

    //retrieve all posts made by a specific user
    public List<Post> getAllUserPost(Long userId);

    //retrieve all posts associated with a specific department
    public List<Post> getAllDepartmentPost(Long departmentId);

    //need to add postTags in here to ensure one call only for creating
    public String createPost(Post post);

    //update
    public String updatePost(Post post, Long postId);

    //softDelete
    public String deletePost(Long postId);
}
