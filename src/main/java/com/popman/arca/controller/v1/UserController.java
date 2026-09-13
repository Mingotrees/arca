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
    public ResponseEntity<?> updateProfilePicture(
            @PathVariable Long userId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal
    ){
        if (!principal.getId().equals(userId) && !principal.isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "forbidden", "message", "Profile picture can only be changed by its owner or an admin"));
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
                    .body(picture.bytes());
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUser() {
        return ResponseEntity.ok(userService.getAllUserV1().stream().map(UserResponse::new).toList());
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
