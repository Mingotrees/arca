package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.auth.RegisterRequest;
import com.popman.arca.dto.v1.common.MessageResponse;
import com.popman.arca.dto.v1.user.ProfilePictureContent;
import com.popman.arca.dto.v1.user.ProfilePictureResponse;
import com.popman.arca.dto.v1.user.UserResponse;
import com.popman.arca.dto.v1.user.UserSummaryResponse;
import com.popman.arca.dto.v1.user.UserUpdateRequest;
import com.popman.arca.entity.UserPrincipal;
import com.popman.arca.service.UserService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserDetails(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(new UserResponse(userService.getUserV1(userId)));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new UserResponse(userService.getUserV1(principal.getId())));
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserSummaryResponse>> searchUsers(@RequestParam("q") String query) {
        return ResponseEntity.ok(userService.searchUsersV1(query).stream().map(UserSummaryResponse::new).toList());
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody UserUpdateRequest request
    ) {
        userService.updateUserV1(principal.getId(), request);
        return ResponseEntity.ok(new UserResponse(userService.getUserV1(principal.getId())));
    }

    @PutMapping(value = "/{userId}/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiResponse(responseCode = "200", description = "Profile picture updated",
            content = @Content(schema = @Schema(implementation = ProfilePictureResponse.class)))
    @io.swagger.v3.oas.annotations.Operation(description = "Owner/admin uploads JPEG, PNG or WebP. Maximum 10 MiB per file/request and 25000000 decoded pixels. Public reads remain supported.")
    @ApiResponse(responseCode = "400", description = "Invalid or mismatched image", content = @Content)
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
    @ApiResponse(responseCode = "403", description = "Only owner/admin may replace avatar", content = @Content)
    @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    @ApiResponse(responseCode = "413", description = "Upload exceeds 10 MiB limit", content = @Content)
    @ApiResponse(responseCode = "415", description = "Unsupported media type or extension", content = @Content)
    public ResponseEntity<?> updateProfilePicture(
            @PathVariable Long userId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal
    ){
        if (!principal.getId().equals(userId) && !principal.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(com.popman.arca.exceptions.ApiErrors.body(403, "forbidden", "Profile picture can only be changed by its owner or an admin"));
        }
        String path = userService.updateProfilePictureV1(userId, file);
        ProfilePictureResponse resp = new ProfilePictureResponse(userId, path, "Profile picture updated successfully");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{userId}/profile-picture")
    @ApiResponse(responseCode = "200", description = "Profile picture",
            content = @Content(mediaType = "image/*", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "404", description = "Profile picture not found", content = @Content)
    public ResponseEntity<byte[]> getProfilePicture(@PathVariable Long userId) {
        try {
            ProfilePictureContent picture = userService.getProfilePictureV1(userId);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(picture.contentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"profile-picture\"")
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(picture.bytes());
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUser() {
        return ResponseEntity.ok(userService.getAllUserV1().stream().map(UserResponse::new).toList());
    }

    @PutMapping(value = "/{userId}/cover-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @io.swagger.v3.oas.annotations.Operation(summary = "Upload or replace user cover", description = "Owner/admin uploads JPEG, PNG or WebP. Maximum 10 MiB per file/request and 25000000 decoded pixels. Replaced objects are removed after commit.")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Cover picture updated", content = @Content(schema = @Schema(implementation = com.popman.arca.dto.v1.user.CoverPictureResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid or mismatched image", content = @Content)
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
    @ApiResponse(responseCode = "403", description = "Only owner/admin may replace cover", content = @Content)
    @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    @ApiResponse(responseCode = "413", description = "Upload exceeds 10 MiB limit", content = @Content)
    @ApiResponse(responseCode = "415", description = "Unsupported media type or extension", content = @Content)
    public ResponseEntity<?> updateCoverPicture(@PathVariable Long userId, @RequestParam("file") MultipartFile file,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        requireCoverOwner(userId, principal);
        String path = userService.updateCoverPictureV1(userId, file);
        return ResponseEntity.ok(new com.popman.arca.dto.v1.user.CoverPictureResponse(userId, path, "Cover picture updated successfully"));
    }

    @GetMapping("/{userId}/cover-picture")
    @io.swagger.v3.oas.annotations.Operation(summary = "Read public user cover", description = "Public cover image. Missing user, reference or stored object returns 404. Responses use no-store and nosniff.", security = {})
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @ApiResponse(responseCode = "200", description = "Cover picture", content = @Content(mediaType = "image/*", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "404", description = "Cover picture not found", content = @Content)
    public ResponseEntity<byte[]> getCoverPicture(@PathVariable Long userId) {
        try {
            ProfilePictureContent picture = userService.getCoverPictureV1(userId);
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(picture.contentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"cover-picture\"")
                    .header("X-Content-Type-Options", "nosniff").header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(picture.bytes());
        } catch (NoSuchElementException e) { return ResponseEntity.notFound().build(); }
    }

    @DeleteMapping("/{userId}/cover-picture")
    @io.swagger.v3.oas.annotations.Operation(summary = "Remove user cover", description = "Owner/admin removes cover. Idempotent for an existing user with no cover; file cleanup occurs after commit.")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Cover removed", content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
    @ApiResponse(responseCode = "403", description = "Only owner/admin may remove cover", content = @Content)
    @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    public ResponseEntity<MessageResponse> deleteCoverPicture(@PathVariable Long userId,
                                                             @AuthenticationPrincipal UserPrincipal principal) {
        requireCoverOwner(userId, principal);
        userService.deleteCoverPictureV1(userId);
        return ResponseEntity.ok(new MessageResponse("Cover picture removed successfully"));
    }

    private void requireCoverOwner(Long userId, UserPrincipal principal) {
        if (!principal.getId().equals(userId) && !principal.isAdmin()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cover picture can only be changed by its owner or an admin");
        }
    }

    @PostMapping
    public ResponseEntity<String> createUser(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(userService.createUserV1(request.toUser()));
    }

    @PutMapping("/{userId}")
    @ApiResponse(responseCode = "200", description = "User updated",
            content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    public ResponseEntity<MessageResponse> updateUser(@PathVariable Long userId, @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(new MessageResponse(userService.updateUserV1(userId, request)));
    }

    @DeleteMapping("/{userId}")
    @ApiResponse(responseCode = "200", description = "User deleted",
            content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    public ResponseEntity<MessageResponse> deleteUser(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(new MessageResponse(userService.deleteUserv1(userId)));
    }
}
