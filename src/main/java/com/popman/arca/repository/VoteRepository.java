package com.popman.arca.repository;

import com.popman.arca.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoteRepository extends JpaRepository<Vote, Long> {
    interface PostVoteCounts {
        Long getPostId();
        Long getUpvotes();
        Long getDownvotes();
    }

    @Query("SELECT v.post.id AS postId, SUM(CASE WHEN v.voteType='UPVOTE' THEN 1L ELSE 0L END) AS upvotes, " +
            "SUM(CASE WHEN v.voteType='DOWNVOTE' THEN 1L ELSE 0L END) AS downvotes " +
            "FROM Vote v WHERE v.post.id IN :ids GROUP BY v.post.id")
    java.util.List<PostVoteCounts> countSearchPageVotes(@Param("ids") java.util.List<Long> ids);


    Optional<Vote> findByPostIdAndUserId(Long postId, Long userId);

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.post.id = :postId AND v.voteType = :voteType")
    Integer countByPostIdAndVoteType(@Param("postId") Long postId, @Param("voteType") String voteType);

    @Query("SELECT COUNT(v) FROM Vote v WHERE v.post.id = :postId")
    Integer countByPostId(@Param("postId") Long postId);

    void deleteByPostIdAndUserId(Long postId, Long userId);
}
