package com.popman.arca.controller;

import com.popman.arca.entity.Post;
import com.popman.arca.service.PostService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
public class PostController {
    PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("{postId}")
    public Post getPost(@PathVariable("postId") Long postId){
        return postService.getPost(postId);
    }

    @GetMapping("/user/{userId}")
    public List<Post> getAllUserPost(@PathVariable("userId") Long userId){
        return postService.getAllUserPost(userId);
    }

    @GetMapping("/department/{departmentId}")
    public List<Post> getAllDepartmentPost(@PathVariable("departmentId") Long departmentId){
        return postService.getAllDepartmentPost(departmentId);
    }

    @PostMapping
    public String createPost(Post newPost){
        return postService.createPost(newPost);
    }

    @PutMapping("{postId}")
    public String updatePost(Post updatedPost, @PathVariable("postId") Long postId){
        return postService.updatePost(updatedPost, postId);
    }

    @DeleteMapping("{postId}")
    public String deletePost(@PathVariable("postId") Long postId){
        return postService.deletePost(postId);
    }
}
