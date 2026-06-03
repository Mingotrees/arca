package com.popman.arca.controller.v1;


import com.popman.arca.dto.v1.vault.EditVaultLabelRequest;
import com.popman.arca.dto.v1.vault.VaultRequest;
import com.popman.arca.entity.UserPrincipal;
import com.popman.arca.entity.Vault;
import com.popman.arca.entity.VaultItem;
import com.popman.arca.service.VaultService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/vaults")
public class VaultController {

    private final VaultService vaultService;

    public VaultController(VaultService vaultService){
        this.vaultService = vaultService;
    }

    @PostMapping("/add")
    public ResponseEntity<?> addToVault(@AuthenticationPrincipal UserPrincipal userDetails,@RequestBody VaultRequest request){
        try {
            Long userId = userDetails.getId();
            Vault vault = vaultService.addToVaultV1(userId, request.getPostId(),request.getLabel());
            return ResponseEntity.ok(vault);
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
            vaultService.removeFromVaultV1(userId, postId);
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
            VaultItem updatedVaultItem = vaultService.editLabelV1(userId, request.getPostId(), request.getNewLabel());

            return ResponseEntity.ok(updatedVaultItem);
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
            Vault vault = vaultService.getUserVaultV1(userId);
            return ResponseEntity.ok(vault);
        }catch (Exception e){
            return ResponseEntity.internalServerError().body("Failed to fetch user vault");
        }
    }

    @GetMapping("/check")
    public ResponseEntity<?> isPostSaved(@AuthenticationPrincipal UserPrincipal userDetails, @RequestParam Long postId){

        try {
            Long userId = userDetails.getId();
            boolean exists = vaultService.isPostSavedV1(userId, postId);
            return ResponseEntity.ok(exists);
        }catch (Exception e){
            return ResponseEntity.internalServerError().body("Error checking post in vault");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getVaultEntry(@PathVariable Long id){
        return vaultService.getVaultEntryV1(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

}
