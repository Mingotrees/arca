package com.popman.arca.controller;


import com.popman.arca.dto.vault.EditVaultLabelRequest;
import com.popman.arca.dto.vault.VaultRequest;
import com.popman.arca.entity.UserPrincipal;
import com.popman.arca.entity.Vault;
import com.popman.arca.service.VaultService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/vaults")
public class VaultController {

    private final VaultService vaultService;

    public VaultController(VaultService vaultService){
        this.vaultService = vaultService;
    }

    @PostMapping("/add")
    public ResponseEntity<?> addToVault(@AuthenticationPrincipal UserPrincipal userDetails,@RequestBody VaultRequest request){
        try {
            Long userId = userDetails.getId();
            Vault savedVault = vaultService.addToVault(userId, request.getPostId(),request.getLabel());
            return ResponseEntity.ok("Post successfully added to Vault.");
        }catch (RuntimeException e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.internalServerError().body("An error has occured");
        }

    }

    @DeleteMapping("/remove")
    public ResponseEntity<?> removeFromVault(@AuthenticationPrincipal UserPrincipal userDetails ,@RequestParam Long postId){
        try {
            Long userId = userDetails.getId();
            vaultService.removeFromVault(userId, postId);
            return ResponseEntity.ok("Post removed from Vault.");
        }catch (RuntimeException e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.internalServerError().body("An error occured while removing post from Vault");
        }
    }


    @PutMapping("/edit-label")
    public ResponseEntity<?> editLabel(@AuthenticationPrincipal UserPrincipal userDetails, @RequestBody EditVaultLabelRequest request){

        try {
            Long userId = userDetails.getId();
            Vault updatedVault = vaultService.editLabel(userId, request.getPostId(), request.getNewLabel());

            return ResponseEntity.ok(updatedVault);
        }catch (RuntimeException e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.internalServerError().body("An error has occurred");
        }
    }

    @GetMapping("/user")
    public ResponseEntity<?> getUserVault(@AuthenticationPrincipal UserPrincipal userDetails){

        try {
            Long userId = userDetails.getId();
            List<Vault> vaultList = vaultService.getUserVault(userId);
            if(vaultList.isEmpty()){
                return ResponseEntity.ok("Vault is empty for this user");
            }
            return ResponseEntity.ok(vaultList);
        }catch (Exception e){
            return ResponseEntity.internalServerError().body("Failed to fetch user vault");
        }
    }

    @GetMapping("/check")
    public ResponseEntity<?> isPostSaved(@AuthenticationPrincipal UserPrincipal userDetails, @RequestBody Long postId){

        try {
            Long userId = userDetails.getId();
            boolean exists = vaultService.isPostSaved(userId, postId);
            return ResponseEntity.ok(exists);
        }catch (Exception e){
            return ResponseEntity.internalServerError().body("Error checking post in vault");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getVaultEntry(@PathVariable Long id){
        return vaultService.getVaultEntry(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

}
