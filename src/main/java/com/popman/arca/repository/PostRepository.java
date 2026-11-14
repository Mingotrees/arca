package com.popman.arca.repository;

import com.popman.arca.entity.Post;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    // All writers for an existing logical post must acquire this lock FIRST.
    // The identity row remains a stable lock anchor even after soft deletion.
    @Query(value = "SELECT identity_row.id FROM post_identities identity_row WHERE identity_row.id = " +
            "(SELECT source.post_id FROM posts source WHERE source.id = :rowId) FOR UPDATE", nativeQuery = true)
    Optional<Long> lockLogicalPostByRowId(@Param("rowId") Long rowId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Post p SET p.isDeleted = true, p.deletedAt = :deletedAt, p.deletedBy = :actorId " +
            "WHERE p.post_id = :postId AND p.isDeleted = false")
    int softDeleteLogicalPost(@Param("postId") Integer postId, @Param("actorId") Long actorId,
                              @Param("deletedAt") LocalDateTime deletedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Post p WHERE p.id = :rowId")
    Optional<Post> findByIdForUpdate(@Param("rowId") Long rowId);

    // A current locking read avoids stale MAX(version) snapshots after waiting on the identity lock.
    @Query(value = "SELECT version FROM posts WHERE post_id = :postId ORDER BY version DESC LIMIT 1 FOR UPDATE",
            nativeQuery = true)
    Integer findMaxVersionByPostId(@Param("postId") Integer postId);

    @Query("SELECT p FROM Post p WHERE p.post_id = :postId AND p.isDeleted = false ORDER BY p.version DESC")
    List<Post> findAllVersionsByPostId(@Param("postId") Integer postId);

    @Query("SELECT p FROM Post p WHERE p.isDeleted = false AND p.status = 'APPROVED' AND p.isLatestVersion = true " +
            "ORDER BY p.updatedAt DESC")
    List<Post> findLatestApprovedPosts();

    @Query("SELECT DISTINCT p FROM Post p JOIN p.subjects s WHERE s.id = :subjectId " +
            "AND p.isDeleted = false AND p.status = 'APPROVED' AND p.isLatestVersion = true " +
            "ORDER BY p.updatedAt DESC")
    List<Post> findLatestApprovedPostsBySubjectId(@Param("subjectId") Long subjectId);

    @Query("SELECT p FROM Post p WHERE p.department.id = :departmentId " +
            "AND p.isDeleted = false AND p.status = 'APPROVED' AND p.isLatestVersion = true " +
            "ORDER BY p.updatedAt DESC")
    List<Post> findLatestApprovedPostsByDepartmentId(@Param("departmentId") Long departmentId);

    @Query("SELECT p FROM Post p WHERE p.isDeleted = false AND p.status = 'PENDING_APPROVAL' " +
            "ORDER BY p.createdAt ASC")

    List<Post> findAllPendingApprovalPosts();

    @Modifying
    @Query("UPDATE Post p SET p.isLatestVersion = :isLatest WHERE p.post_id = :postId AND p.isDeleted = false")
    void updateIsLatestVersionByPostId(@Param("postId") Integer postId,
                                       @Param("isLatest") Boolean isLatest);

    @Query("SELECT p FROM Post p WHERE p.post_id = :postId " +
            "AND p.isDeleted = false AND p.version < :currentVersion AND p.status = 'APPROVED' " +
            "ORDER BY p.version DESC LIMIT 1")
    Post findPreviousApprovedVersion(@Param("postId") Integer postId,
                                     @Param("currentVersion") Integer currentVersion);

    @Query("SELECT p FROM Post p WHERE p.departmentId = :departmentId AND p.isDeleted = false AND p.status = 'PENDING_APPROVAL'")
    List<Post> getPendingPostsByDepartment(@Param("departmentId") Long departmentId);

    @Query("SELECT p FROM Post p WHERE p.user.id = :userId " +
            "AND p.isDeleted = false AND p.status = 'APPROVED' AND p.isLatestVersion = true " +
            "ORDER BY p.updatedAt DESC")
    List<Post> findLatestApprovedPostsByUserId(@Param("userId") Long userId);

    @Query("SELECT p FROM Post p WHERE p.userId = :userId AND p.isDeleted = false " +
            "AND p.version = (SELECT MAX(p2.version) FROM Post p2 WHERE p2.post_id = p.post_id AND p2.isDeleted = false) " +
            "ORDER BY p.updatedAt DESC")
    List<Post> findAllPostsByUserId(@Param("userId") Long userId);

    @Query("SELECT p FROM Post p WHERE p.userId = :userId AND p.status = :status AND p.isDeleted = false " +
            "AND p.version = (SELECT MAX(p2.version) FROM Post p2 WHERE p2.post_id = p.post_id AND p2.isDeleted = false) " +
            "ORDER BY p.updatedAt DESC")
    List<Post> findAllPostsByUserIdAndStatus(@Param("userId") Long userId,
                                             @Param("status") String status);

}
