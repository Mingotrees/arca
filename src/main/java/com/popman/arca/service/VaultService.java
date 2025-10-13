package com.popman.arca.service;

import com.popman.arca.entity.Vault;

import java.util.List;
import java.util.Optional;

public interface VaultService {

    Vault addToVault(Long userId, Long postId, String label);
    void removeFromVault(Long userId, Long postId);
    List<Vault> getUserVault(Long userId);
    Optional<Vault>getVaultEntry(Long id); //Get the specific post from the vault
    boolean isPostSaved(Long userId, Long postId); //For preventing Duplicates, it checks if the user already saved this particular post
    Vault editLabel(Long userId, Long postId, String newLabel);
}
