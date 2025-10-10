package com.popman.arca.repository;

import com.popman.arca.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    @Query("SELECT MAX(p.post_id) FROM Post p")
    Integer findMaxPostId();
}