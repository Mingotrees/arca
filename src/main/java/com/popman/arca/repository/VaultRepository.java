package com.popman.arca.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.popman.arca.entity.Vault;

import java.util.List;
import java.util.Optional;

@Repository
public interface VaultRepository extends JpaRepository<Vault, Long> {
    List<Vault> findByUserId(Long userId);
    boolean existsByUserIdAndPostId(Long userId, Long postId);
    Optional<Vault> findByUserIdAndPostId(Long userId, Long postId);

}
