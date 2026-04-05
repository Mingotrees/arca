package com.popman.arca.repository;

import com.popman.arca.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    @Query("SELECT COALESCE(MAX(p.post_id), 0) + 1 FROM Post p")
    Integer getNextPostId();

    @Query("SELECT MAX(p.version) FROM Post p WHERE p.post_id = :postId")
    Integer findMaxVersionByPostId(@Param("postId") Integer postId);

    @Query("SELECT p FROM Post p WHERE p.post_id = :postId ORDER BY p.version DESC")
    List<Post> findAllVersionsByPostId(@Param("postId") Integer postId);

    @Query("SELECT p FROM Post p WHERE p.department.id = :departmentId " +
            "AND p.status = 'APPROVED' AND p.isLatestVersion = true " +
            "ORDER BY p.updatedAt DESC")
    List<Post> findLatestApprovedPostsByDepartmentId(@Param("departmentId") Long departmentId);

    @Query("SELECT p FROM Post p WHERE p.status = 'PENDING_APPROVAL' " +
            "ORDER BY p.createdAt ASC")

    List<Post> findAllPendingApprovalPosts();

    @Modifying
    @Query("UPDATE Post p SET p.isLatestVersion = :isLatest WHERE p.post_id = :postId")
    void updateIsLatestVersionByPostId(@Param("postId") Integer postId,
                                       @Param("isLatest") Boolean isLatest);

    @Query(value = "SELECT * FROM posts p WHERE p.post_id = :postId " +
            "AND p.version < :currentVersion AND p.status = 'APPROVED' " +
            "ORDER BY p.version DESC LIMIT 1", nativeQuery = true)
    Post findPreviousApprovedVersion(@Param("postId") Integer postId,
                                     @Param("currentVersion") Integer currentVersion);

    @Query("SELECT p FROM Post p WHERE p.departmentId = :departmentId AND p.status = 'PENDING_APPROVAL'")
    List<Post> getPendingPostsByDepartment(@Param("departmentId") Long departmentId);

    @Query("SELECT p FROM Post p WHERE p.user.id = :userId " +
            "AND p.status = 'APPROVED' AND p.isLatestVersion = true " +
            "ORDER BY p.updatedAt DESC")
    List<Post> findLatestApprovedPostsByUserId(@Param("userId") Long userId);

}