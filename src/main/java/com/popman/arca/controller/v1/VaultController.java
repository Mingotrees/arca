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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("api/v1/vaults")
@ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
public class VaultController {

    private final VaultService vaultService;

    public VaultController(VaultService vaultService){
        this.vaultService = vaultService;
    }

    @PostMapping("/add")
    @Operation(description = "Request post_id is a post version database row id, not logical post_id. Parent post must be readable; saved entry belongs to authenticated user.")
    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content)
    @ApiResponse(responseCode = "409", description = "Post already saved by current user", content = @Content)
    @ApiResponse(responseCode = "200", description = "Post saved",
            content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    @ApiResponse(responseCode = "404", description = "Post not found, including deleted posts", content = @Content)
    public ResponseEntity<?> addToVault(@AuthenticationPrincipal UserPrincipal userDetails,@jakarta.validation.Valid @RequestBody VaultRequest request){
        vaultService.addToVaultV1(userDetails.getId(), request.getPostId(), request.getLabel());
        return ResponseEntity.ok(new MessageResponse("Post successfully added to Vault."));
    }

    @DeleteMapping("/remove")
    @ApiResponse(responseCode = "200", description = "Post removed",
            content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    @ApiResponse(responseCode = "404", description = "Post not found, including deleted posts", content = @Content)
    public ResponseEntity<?> removeFromVault(@AuthenticationPrincipal UserPrincipal userDetails,
            @Parameter(description = "Post version database row id, not logical post_id") @RequestParam Long postId){
        vaultService.removeFromVaultV1(userDetails.getId(), postId);
        return ResponseEntity.ok(new MessageResponse("Post removed from Vault."));
    }


    @PutMapping("/edit-label")
    @Operation(description = "Request post_id is a post version database row id, not logical post_id. Deleted posts are inaccessible.")
    @ApiResponse(responseCode = "200", description = "Label updated",
            content = @Content(schema = @Schema(implementation = VaultResponse.class)))
    @ApiResponse(responseCode = "404", description = "Post not found, including deleted posts", content = @Content)
    public ResponseEntity<?> editLabel(@AuthenticationPrincipal UserPrincipal userDetails, @jakarta.validation.Valid @RequestBody EditVaultLabelRequest request){

        Vault updatedVault = vaultService.editLabelV1(userDetails.getId(), request.getPostId(), request.getNewLabel());
        return ResponseEntity.ok(new VaultResponse(updatedVault));
    }

    @GetMapping("/user")
    @Operation(description = "Returns current user's saved entries with readable parent posts only; inaccessible retained entries are hidden, not physically purged.")
    @ApiResponse(responseCode = "200", description = "Saved posts",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = VaultResponse.class))))
    public ResponseEntity<?> getUserVault(@AuthenticationPrincipal UserPrincipal userDetails){

        List<Vault> vaultList = vaultService.getUserVaultV1(userDetails.getId());
        return ResponseEntity.ok(vaultList.stream().map(VaultResponse::new).toList());
    }

    @GetMapping("/check/{postId}")
    @ApiResponse(responseCode = "200", description = "Saved state",
            content = @Content(schema = @Schema(implementation = Boolean.class)))
    @ApiResponse(responseCode = "404", description = "Post not found, including deleted posts", content = @Content)
    public ResponseEntity<?> isPostSaved(@AuthenticationPrincipal UserPrincipal userDetails,
            @Parameter(description = "Post version database row id, not logical post_id") @PathVariable Long postId){

        return ResponseEntity.ok(vaultService.isPostSavedV1(userDetails.getId(), postId));
    }

    @GetMapping("/{id}")
    @ApiResponse(responseCode = "200", description = "Vault entry",
            content = @Content(schema = @Schema(implementation = VaultResponse.class)))
    @ApiResponse(responseCode = "404", description = "Vault entry not found, including entries of deleted posts", content = @Content)
    public ResponseEntity<VaultResponse> getVaultEntry(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal userDetails){
        return vaultService.getVaultEntryV1(id, userDetails.getId())
                .map(VaultResponse::new)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}
