package com.popman.arca.service.impl;

import com.popman.arca.entity.Post;
import com.popman.arca.entity.User;
import com.popman.arca.entity.Vault;
import com.popman.arca.repository.PostRepository;
import com.popman.arca.repository.VaultRepository;
import com.popman.arca.repository.UserRepository;
import com.popman.arca.service.VaultService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class VaultServiceImplementation implements VaultService {

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

        try {
            if(vaultRepository.existsByUserIdAndPostId(userId, postId)){
                throw new IllegalStateException("Post already saved to Vault");
            }
            User user = userRepository.findById(userId).orElseThrow(()-> new RuntimeException("User not found with ID: " + userId));
            Post post = postRepository.findById(postId).orElseThrow(()-> new RuntimeException("Post not Found with ID: " + postId));
            Vault vault = new Vault();
            vault.setUser(user);
            vault.setPost(post);
            vault.setLabel(label);

            return vaultRepository.save(vault);
        }catch (Exception e){
            throw new RuntimeException("Failed to add post to Vault: " + e.getMessage(), e);
        }
    }

    @Override
    public void removeFromVaultV1(Long userId, Long postId){

        try{
            Optional<Vault> vaultEntry = vaultRepository.findByUserIdAndPostId(userId, postId);
            if(vaultEntry.isEmpty()){
                throw new RuntimeException("No vault entry found for this user and post");
            }
            vaultRepository.delete(vaultEntry.get());
        }catch (Exception e){
            throw new RuntimeException("Failed to remove post from Vault");
        }
    }

    @Override
    public Vault editLabelV1(Long userId, Long postId, String newLabel) {

        try {
            Vault vault = vaultRepository.findByUserIdAndPostId(userId, postId).orElseThrow(()-> new RuntimeException("Vault entry not found for this user and post"));
            vault.setLabel(newLabel);
            return vaultRepository.save(vault);

        }catch (RuntimeException e){
            throw new RuntimeException("Failed to edit Vault Label: " + e.getMessage(), e);
        }catch (Exception e){
            throw new RuntimeException("An error has occured");
        }
    }

    @Override
    public List<Vault> getUserVaultV1(Long userId){
        try {
            return vaultRepository.findByUserId(userId);
        }catch (Exception e){
            throw new RuntimeException("Failed to retrieve user vault: " + e.getMessage(), e);
        }

    }

    @Override
    public Optional<Vault> getVaultEntryV1(Long id) {
        return Optional.empty();
    }

    @Override
    public boolean isPostSavedV1(Long userId, Long postId){
        try {
            return vaultRepository.existsByUserIdAndPostId(userId, postId);
        }catch(Exception e){
            throw new RuntimeException("Failed to check if post is saved: " + e.getMessage());
        }
    }
}
