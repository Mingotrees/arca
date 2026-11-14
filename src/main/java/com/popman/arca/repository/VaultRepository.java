package com.popman.arca.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.popman.arca.entity.Vault;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface VaultRepository extends JpaRepository<Vault, Long> {
    @Query("SELECT v FROM Vault v WHERE v.user.id = :userId AND v.post.isDeleted = false")
    List<Vault> findByUserId(@Param("userId") Long userId);
    @Query("SELECT (COUNT(v) > 0) FROM Vault v WHERE v.user.id = :userId AND v.post.id = :postId AND v.post.isDeleted = false")
    boolean existsByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);
    @Query("SELECT v FROM Vault v WHERE v.user.id = :userId AND v.post.id = :postId AND v.post.isDeleted = false")
    Optional<Vault> findByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);
    @Query("SELECT v FROM Vault v WHERE v.id = :id AND v.user.id = :userId AND v.post.isDeleted = false")
    Optional<Vault> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

}
