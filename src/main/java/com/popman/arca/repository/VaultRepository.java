package com.popman.arca.repository;

import com.popman.arca.entity.Vault;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VaultRepository extends JpaRepository<Vault, Long> {

    @EntityGraph(attributePaths = {"savedPosts", "savedPosts.post"})
    Optional<Vault> findByUserId(Long userId);

}
