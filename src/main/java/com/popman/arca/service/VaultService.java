package com.popman.arca.service;

import com.popman.arca.entity.Vault;

import java.util.List;
import java.util.Optional;

public interface VaultService {

    Vault addToVaultV1(Long userId, Long postId, String label);
    void removeFromVaultV1(Long userId, Long postId);
    List<Vault> getUserVaultV1(Long userId);
    Optional<Vault> getVaultEntryV1(Long id); //Get the specific post from the vault
    boolean isPostSavedV1(Long userId, Long postId); //For preventing Duplicates, it checks if the user already saved this particular post
    Vault editLabelV1(Long userId, Long postId, String newLabel);
}
