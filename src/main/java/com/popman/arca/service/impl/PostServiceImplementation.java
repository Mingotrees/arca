package com.popman.arca.service.impl;


import com.popman.arca.entity.Post;
import com.popman.arca.repository.PostRepository;
import com.popman.arca.service.PostService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostServiceImplementation implements PostService {
    private PostRepository postRepository;

    @Override
    public Post getPost(Long postId) {
        return null;
    }

    @Override
    public List<Post> getAllUserPost(Long userId) {
        return List.of();
    }

    @Override
    public List<Post> getAllDepartmentPost(Long departmentId) {
        return List.of();
    }

    @Override
    public String createPost(Post post) {
        //create a post_id generator
        return "";
    }

    @Override
    public String updatePost(Post post, Long postId) {
        return "";
    }

    @Override
    public String deletePost(Long postId) {
        return "";
    }
}
