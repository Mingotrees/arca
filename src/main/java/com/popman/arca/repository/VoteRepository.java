package com.popman.arca.repository;

import com.popman.arca.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoteRepository extends JpaRepository<Vote, Long> {

    Optional<Vote> findByPostIdAndUserId(Long postId, Long userId);

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.post.id = :postId AND v.voteType = :voteType")
    Integer countByPostIdAndVoteType(@Param("postId") Long postId, @Param("voteType") String voteType);

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.post.id = :postId")
    Integer countByPostId(@Param("postId") Long postId);

    void deleteByPostIdAndUserId(Long postId, Long userId);
}