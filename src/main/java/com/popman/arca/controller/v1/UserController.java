package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.auth.RegisterRequest;
import com.popman.arca.dto.v1.user.ProfilePictureResponse;
import com.popman.arca.dto.v1.user.UserResponse;
import com.popman.arca.entity.User;
import com.popman.arca.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserDetails(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(mapToUserResponse(userService.getUserV1(userId)));
    }

    @PutMapping(value = "/{userId}/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProfilePictureResponse> updateProfilePicture(
            @PathVariable Long userId,
            @RequestParam("file") MultipartFile file
    ){
        String path = userService.updateProfilePictureV1(userId, file);
        ProfilePictureResponse resp = new ProfilePictureResponse(userId, path, "Profile picture updated successfully");
        return ResponseEntity.ok(resp);
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUser() {
        List<UserResponse> response = userService.getAllUserV1().stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<String> createUser(@Valid @RequestBody RegisterRequest registerRequest) {
        return ResponseEntity.ok(userService.createUserV1(registerRequest));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<String> updateUser(@PathVariable Long userId, @RequestBody User user) {
        user.setId(userId);
        return ResponseEntity.ok(userService.updateUserV1(user));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<String> deleteUser(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(userService.deleteUserv1(userId));
    }

    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getCourse(),
                user.getDepartment(),
                user.getBio(),
                user.getProfilePicture(),
                user.getRoles()
        );
    }
}