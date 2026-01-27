package com.popman.arca.controller.v1;

import com.popman.arca.entity.User;
import com.popman.arca.service.BannedEmailService;
import com.popman.arca.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")  // All endpoints require ADMIN role
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);
    private final UserService userService;
    private final BannedEmailService bannedEmailService;

    public AdminController(UserService userService, BannedEmailService bannedEmailService) {
        this.userService = userService;
        this.bannedEmailService = bannedEmailService;
    }

    // Get all users (admin only)
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        try {
            List<User> users = userService.getAllUserV1();
            logger.info("Admin retrieved {} users", users.size());
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            logger.error("Error retrieving users: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to retrieve users"));
        }
    }

    // Get specific user
    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        try {
            User user = userService.getUserV1(id);
            logger.info("Admin retrieved user: {}", id);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            logger.error("Error retrieving user {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    // Create admin user
    @PostMapping("/create-admin")
    public ResponseEntity<?> createAdmin(@RequestBody User user) {
        try {
            String result = userService.createAdminV1(user);
            logger.info("Admin created new admin user: {}", user.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new SuccessResponse(result));
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to create admin: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error creating admin: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to create admin"));
        }
    }

    // Promote user to admin
    @PutMapping("/users/{id}/promote")
    public ResponseEntity<?> promoteToAdmin(@PathVariable Long id) {
        try {
            String result = userService.promoteToAdminV1(id);
            logger.info("User {} promoted to admin", id);
            return ResponseEntity.ok(new SuccessResponse(result));
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to promote user {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error promoting user {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to promote user"));
        }
    }

    // Demote admin to regular user
    @PutMapping("/users/{id}/demote")
    public ResponseEntity<?> demoteFromAdmin(@PathVariable Long id) {
        try {
            String result = userService.demoteFromAdminV1(id);
            logger.info("User {} demoted from admin", id);
            return ResponseEntity.ok(new SuccessResponse(result));
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to demote user {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error demoting user {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to demote user"));
        }
    }

    // Add custom role to user
    @PutMapping("/users/{id}/roles/add")
    public ResponseEntity<?> addRole(@PathVariable Long id, @RequestBody RoleRequest roleRequest) {
        try {
            String result = userService.addRoleToUserV1(id, roleRequest.getRole());
            logger.info("Role {} added to user {}", roleRequest.getRole(), id);
            return ResponseEntity.ok(new SuccessResponse(result));
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to add role to user {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error adding role to user {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to add role"));
        }
    }

    // Remove role from user
    @PutMapping("/users/{id}/roles/remove")
    public ResponseEntity<?> removeRole(@PathVariable Long id, @RequestBody RoleRequest roleRequest) {
        try {
            String result = userService.removeRoleFromUserV1(id, roleRequest.getRole());
            logger.info("Role {} removed from user {}", roleRequest.getRole(), id);
            return ResponseEntity.ok(new SuccessResponse(result));
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to remove role from user {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error removing role from user {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to remove role"));
        }
    }

    // Delete user (soft delete)
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            String result = userService.deleteUserv1(id);
            logger.info("User {} deleted by admin", id);
            return ResponseEntity.ok(new SuccessResponse(result));
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to delete user {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error deleting user {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to delete user"));
        }
    }

    // Banned email management
    @GetMapping("/banned-emails")
    public ResponseEntity<?> listBannedEmails() {
        try {
            return ResponseEntity.ok(bannedEmailService.listAllV1());
        } catch (Exception e) {
            logger.error("Error listing banned emails: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to list banned emails"));
        }
    }

    @PostMapping("/banned-emails")
    public ResponseEntity<?> banEmail(@RequestBody BanEmailRequest request) {
        try {
            String msg = bannedEmailService.banV1(request.getEmail(), request.getReason());
            return ResponseEntity.status(HttpStatus.CREATED).body(new SuccessResponse(msg));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error banning email: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to ban email"));
        }
    }

    @DeleteMapping("/banned-emails/{email}")
    public ResponseEntity<?> unbanEmail(@PathVariable String email) {
        try {
            String msg = bannedEmailService.unbanV1(email);
            return ResponseEntity.ok(new SuccessResponse(msg));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            logger.error("Error unbanning email: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to unban email"));
        }
    }

    // DTOs
    private static class RoleRequest {
        private String role;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    private static class BanEmailRequest {
        private String email;
        private String reason;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    private static class SuccessResponse {
        private String message;

        public SuccessResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    private static class ErrorResponse {
        private String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }
    }
}