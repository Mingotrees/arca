package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.user.ProfilePictureResponse;
import com.popman.arca.entity.User;
import com.popman.arca.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<User> getUserDetails(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(userService.getUserV1(userId));
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
    public ResponseEntity<List<User>> getAllUser() {
        return ResponseEntity.ok(userService.getAllUserV1());
    }

    @PostMapping
    public ResponseEntity<String> createUser(@RequestBody User user) {
        return ResponseEntity.ok(userService.createUserV1(user));
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
}