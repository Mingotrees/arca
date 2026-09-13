package com.popman.arca.controller.v1;


import com.popman.arca.dto.v1.vault.EditVaultLabelRequest;
import com.popman.arca.dto.v1.vault.VaultRequest;
import com.popman.arca.dto.v1.vault.VaultResponse;
import com.popman.arca.dto.v1.common.MessageResponse;
import com.popman.arca.entity.UserPrincipal;
import com.popman.arca.entity.Vault;
import com.popman.arca.service.VaultService;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/vaults")
public class VaultController {

    private final VaultService vaultService;

    public VaultController(VaultService vaultService){
        this.vaultService = vaultService;
    }

    @PostMapping("/add")
    @ApiResponse(responseCode = "200", description = "Post saved",
            content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    public ResponseEntity<?> addToVault(@AuthenticationPrincipal UserPrincipal userDetails,@RequestBody VaultRequest request){
        try {
            Long userId = userDetails.getId();
            Vault savedVault = vaultService.addToVaultV1(userId, request.getPostId(),request.getLabel());
            return ResponseEntity.ok(new MessageResponse("Post successfully added to Vault."));
        }catch (RuntimeException e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.internalServerError().body("An error has occured");
        }

    }

    @DeleteMapping("/remove")
    @ApiResponse(responseCode = "200", description = "Post removed",
            content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    public ResponseEntity<?> removeFromVault(@AuthenticationPrincipal UserPrincipal userDetails ,@RequestParam Long postId){
        try {
            Long userId = userDetails.getId();
            vaultService.removeFromVaultV1(userId, postId);
            return ResponseEntity.ok(new MessageResponse("Post removed from Vault."));
        }catch (RuntimeException e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.internalServerError().body("An error occured while removing post from Vault");
        }
    }


    @PutMapping("/edit-label")
    @ApiResponse(responseCode = "200", description = "Label updated",
            content = @Content(schema = @Schema(implementation = VaultResponse.class)))
    public ResponseEntity<?> editLabel(@AuthenticationPrincipal UserPrincipal userDetails, @RequestBody EditVaultLabelRequest request){

        try {
            Long userId = userDetails.getId();
            Vault updatedVault = vaultService.editLabelV1(userId, request.getPostId(), request.getNewLabel());

            return ResponseEntity.ok(new VaultResponse(updatedVault));
        }catch (RuntimeException e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.internalServerError().body("An error has occurred");
        }
    }

    @GetMapping("/user")
    @ApiResponse(responseCode = "200", description = "Saved posts",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = VaultResponse.class))))
    public ResponseEntity<?> getUserVault(@AuthenticationPrincipal UserPrincipal userDetails){

        try {
            Long userId = userDetails.getId();
            List<Vault> vaultList = vaultService.getUserVaultV1(userId);
            return ResponseEntity.ok(vaultList.stream().map(VaultResponse::new).toList());
        }catch (Exception e){
            return ResponseEntity.internalServerError().body("Failed to fetch user vault");
        }
    }

    @GetMapping("/check/{postId}")
    @ApiResponse(responseCode = "200", description = "Saved state",
            content = @Content(schema = @Schema(implementation = Boolean.class)))
    public ResponseEntity<?> isPostSaved(@AuthenticationPrincipal UserPrincipal userDetails, @PathVariable Long postId){

        try {
            Long userId = userDetails.getId();
            boolean exists = vaultService.isPostSavedV1(userId, postId);
            return ResponseEntity.ok(exists);
        }catch (Exception e){
            return ResponseEntity.internalServerError().body("Error checking post in vault");
        }
    }

    @GetMapping("/{id}")
    @ApiResponse(responseCode = "200", description = "Vault entry",
            content = @Content(schema = @Schema(implementation = VaultResponse.class)))
    public ResponseEntity<VaultResponse> getVaultEntry(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userDetails){
        return vaultService.getVaultEntryV1(id, userDetails.getId())
                .map(VaultResponse::new)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}
