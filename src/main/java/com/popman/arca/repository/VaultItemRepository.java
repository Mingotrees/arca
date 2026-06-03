package com.popman.arca.repository;

import com.popman.arca.entity.VaultItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VaultItemRepository extends JpaRepository<VaultItem, Long> {

    Optional<VaultItem> findByVault_IdAndPost_Id(Long vaultId, Long postId);
}



