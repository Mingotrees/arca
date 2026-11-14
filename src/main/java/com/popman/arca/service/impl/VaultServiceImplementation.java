package com.popman.arca.service.impl;

import com.popman.arca.entity.Post;
import com.popman.arca.entity.User;
import com.popman.arca.entity.Vault;
import com.popman.arca.repository.PostRepository;
import com.popman.arca.repository.VaultRepository;
import com.popman.arca.repository.UserRepository;
import com.popman.arca.service.VaultService;
import com.popman.arca.service.PostAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class VaultServiceImplementation implements VaultService {

    private final PostAccessPolicy accessPolicy = new PostAccessPolicy();

    private final VaultRepository vaultRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;

    public VaultServiceImplementation(VaultRepository vaultRepository, UserRepository userRepository, PostRepository postRepository) {
        this.vaultRepository = vaultRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
    }

    @Override
    public Vault addToVaultV1(Long userId, Long postId, String label){
        Post post = lockActivePost(postId);
        if (vaultRepository.existsByUserIdAndPostId(userId, postId)) {
            throw new IllegalStateException("Post already saved to Vault");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new java.util.NoSuchElementException("User not found"));
        Vault vault = new Vault();
        vault.setUser(user);
        vault.setPost(post);
        vault.setLabel(label);
        return vaultRepository.save(vault);
    }

    @Override
    public void removeFromVaultV1(Long userId, Long postId){
        lockActivePost(postId);
        Vault entry = vaultRepository.findByUserIdAndPostId(userId, postId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Vault entry not found"));
        vaultRepository.delete(entry);
    }

    @Override
    public Vault editLabelV1(Long userId, Long postId, String newLabel) {
        lockActivePost(postId);
        Vault vault = vaultRepository.findByUserIdAndPostId(userId, postId)
                .orElseThrow(() -> new java.util.NoSuchElementException("Vault entry not found"));
        vault.setLabel(newLabel);
        return vaultRepository.save(vault);
    }

    @Override
    public List<Vault> getUserVaultV1(Long userId){
        return vaultRepository.findByUserId(userId).stream()
                .filter(vault -> accessPolicy.canRead(vault.getPost())).toList();
    }

    @Override
    public Optional<Vault> getVaultEntryV1(Long id, Long userId) {
        return vaultRepository.findByIdAndUserId(id, userId)
                .filter(vault -> accessPolicy.canRead(vault.getPost()));
    }

    @Override
    public boolean isPostSavedV1(Long userId, Long postId){
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
        accessPolicy.requireReadable(post);
        return vaultRepository.existsByUserIdAndPostId(userId, postId);
    }

    private Post lockActivePost(Long postId) {
        postRepository.lockLogicalPostByRowId(postId);
        Post post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
        accessPolicy.requireReadable(post);
        return post;
    }

}
