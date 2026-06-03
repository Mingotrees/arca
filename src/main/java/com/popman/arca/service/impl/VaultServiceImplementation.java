package com.popman.arca.service.impl;

import com.popman.arca.entity.Post;
import com.popman.arca.entity.User;
import com.popman.arca.entity.Vault;
import com.popman.arca.entity.VaultItem;
import com.popman.arca.repository.PostRepository;
import com.popman.arca.repository.VaultItemRepository;
import com.popman.arca.repository.VaultRepository;
import com.popman.arca.repository.UserRepository;
import com.popman.arca.service.VaultService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class VaultServiceImplementation implements VaultService {

    private static final Logger logger = LoggerFactory.getLogger(VaultServiceImplementation.class);

    private final VaultRepository vaultRepository;
    private final VaultItemRepository vaultItemRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;

    public VaultServiceImplementation(VaultRepository vaultRepository, VaultItemRepository vaultItemRepository, UserRepository userRepository, PostRepository postRepository) {
        this.vaultRepository = vaultRepository;
        this.vaultItemRepository = vaultItemRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
    }

    @Override
    @Transactional
    public Vault addToVaultV1(Long userId, Long postId, String label){
        validateIds(userId, postId);

        User user = userRepository.findById(userId)
                .orElseThrow(()-> new NoSuchElementException("User not found with id: " + userId));
        Post post = postRepository.findById(postId)
                .orElseThrow(()-> new NoSuchElementException("Post not found with id: " + postId));
        Vault vault = getOrCreateVault(user);

        try {
            VaultItem vaultItem = new VaultItem(vault, post, label);
            vault.addSavedPost(vaultItem);
            VaultItem savedItem = vaultItemRepository.saveAndFlush(vaultItem);
            logger.info("Vault item save succeeded: userId={}, postId={}, vaultId={}, vaultItemId={}", userId, postId, vault.getId(), savedItem.getId());
            return vault;
        } catch (DataIntegrityViolationException ex) {
            logger.warn("Vault item save rejected as duplicate: userId={}, postId={}", userId, postId);
            throw new IllegalStateException("Post already saved to Vault", ex);
        }
    }

    @Override
    @Transactional
    public void removeFromVaultV1(Long userId, Long postId){
        validateIds(userId, postId);
        Vault vault = vaultRepository.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("Vault not found for this user"));

        VaultItem vaultItem = vaultItemRepository.findByVault_IdAndPost_Id(vault.getId(), postId)
                .orElseThrow(() -> new NoSuchElementException("No vault entry found for this user and post"));

        vault.removeSavedPost(vaultItem);
        vaultItemRepository.delete(vaultItem);
        logger.info("Vault item removed: userId={}, postId={}, vaultId={}, vaultItemId={}", userId, postId, vault.getId(), vaultItem.getId());
    }

    @Override
    @Transactional
    public VaultItem editLabelV1(Long userId, Long postId, String newLabel) {
        validateIds(userId, postId);
        validateLabel(newLabel);

        Vault vault = vaultRepository.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("Vault not found for this user"));

        VaultItem vaultItem = vaultItemRepository.findByVault_IdAndPost_Id(vault.getId(), postId)
                .orElseThrow(()-> new NoSuchElementException("Vault entry not found for this user and post"));
        vaultItem.setLabel(newLabel);
        VaultItem saved = vaultItemRepository.save(vaultItem);
        logger.info("Vault label updated: userId={}, postId={}, vaultId={}, vaultItemId={}", userId, postId, vault.getId(), saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public Vault getUserVaultV1(Long userId){
        validateUserId(userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found with id: " + userId));

        Vault vault = vaultRepository.findByUserId(userId)
                .orElseGet(() -> createVaultForUser(user));
        logger.debug("Loaded vault for user {} with {} saved posts", userId, vault.getSavedPosts().size());
        return vault;
    }

    @Override
    public Optional<VaultItem> getVaultEntryV1(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Vault entry ID must be a positive number");
        }
        return vaultItemRepository.findById(id);
    }

    @Override
    public boolean isPostSavedV1(Long userId, Long postId){
        validateIds(userId, postId);

        return vaultRepository.findByUserId(userId)
                .flatMap(vault -> vaultItemRepository.findByVault_IdAndPost_Id(vault.getId(), postId))
                .isPresent();
    }

    private Vault getOrCreateVault(User user) {
        return vaultRepository.findByUserId(user.getId())
                .orElseGet(() -> createVaultForUser(user));
    }

    private Vault createVaultForUser(User user) {
        try {
            Vault vault = new Vault(user);
            Vault saved = vaultRepository.saveAndFlush(vault);
            logger.info("Created vault for user {} with vaultId={}", user.getId(), saved.getId());
            return saved;
        } catch (DataIntegrityViolationException ex) {
            logger.debug("Vault creation raced for user {}, reloading existing vault", user.getId());
            return vaultRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new IllegalStateException("Unable to create or load vault for user " + user.getId(), ex));
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("User ID must be a positive number");
        }
    }

    private void validateIds(Long userId, Long postId) {
        validateUserId(userId);
        if (postId == null || postId <= 0) {
            throw new IllegalArgumentException("Post ID must be a positive number");
        }
    }

    private void validateLabel(String newLabel) {
        if (newLabel == null || newLabel.trim().isEmpty()) {
            throw new IllegalArgumentException("Label cannot be blank");
        }
    }

}
