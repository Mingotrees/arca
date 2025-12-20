package com.popman.arca.service.impl;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.popman.arca.entity.User;
import com.popman.arca.repository.UserRepository;
import com.popman.arca.service.UserService;
import jakarta.transaction.Transactional;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class UserServiceImplementation implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImplementation.class);
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Storage storage;

    @Value("${spring.cloud.gcp.storage.bucket:arca-uploads}")
    private String bucketName;

    public UserServiceImplementation(UserRepository userRepository, PasswordEncoder passwordEncoder, Storage storage) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.storage = storage;
    }

    private String buildPublicUrl(String objectName) {
        return "https://storage.googleapis.com/" + bucketName + "/" + objectName;
    }

    @Override
    public User getUser(Long userId) {
        try {
            logger.debug("Fetching user with ID: {}", userId);
            return userRepository.findById(userId)
                    .orElseThrow(() -> new NoSuchElementException("User not found with ID: " + userId));
        } catch (IllegalArgumentException | NoSuchElementException e) {
            logger.error("Error fetching user: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public List<User> getAllUser() {
        try {
            logger.debug("Fetching all users");
            return userRepository.findAll();
        } catch (Exception e) {
            logger.error("Error fetching all users: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch users", e);
        }
    }

    @Override
    @Transactional
    public String createUser(User user) {
        try {
            logger.debug("Creating new user: {}", user.getEmail());
            validateUser(user);
            user.setId(null);

            // Check if user already exists
            if (userRepository.findByEmail(user.getEmail()) != null) {
                throw new IllegalArgumentException("User with email " + user.getEmail() + " already exists");
            }

            // Encode password
            user.setPassword(passwordEncoder.encode(user.getPassword()));

            // Set default role if not already set
            if (user.getRoles() == null || user.getRoles().isEmpty()) {
                Set<String> roles = new HashSet<>();
                roles.add("ROLE_USER");
                user.setRoles(roles);
                logger.debug("Assigned default ROLE_USER to new user");
            }

            User savedUser = userRepository.save(user);
            logger.info("User created successfully with ID: {} and roles: {}",
                    savedUser.getId(), savedUser.getRoles());

            return "User created successfully with ID: " + savedUser.getId();
        } catch (IllegalArgumentException e) {
            logger.error("Validation error creating user: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error creating user: {}", e.getMessage());
            throw new RuntimeException("Error creating user: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public String createAdmin(User user) {
        try {
            logger.debug("Creating new admin user: {}", user.getEmail());
            validateUser(user);
            user.setId(null);

            // Check if user already exists
            if (userRepository.findByEmail(user.getEmail()) != null) {
                throw new IllegalArgumentException("User with email " + user.getEmail() + " already exists");
            }

            // Encode password
            user.setPassword(passwordEncoder.encode(user.getPassword()));

            // Set admin roles
            Set<String> roles = new HashSet<>();
            roles.add("ROLE_USER");
            roles.add("ROLE_ADMIN");
            user.setRoles(roles);

            User savedUser = userRepository.save(user);
            logger.info("Admin created successfully with ID: {} and roles: {}",
                    savedUser.getId(), savedUser.getRoles());

            return "Admin created successfully with ID: " + savedUser.getId();
        } catch (IllegalArgumentException e) {
            logger.error("Validation error creating admin: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error creating admin: {}", e.getMessage());
            throw new RuntimeException("Error creating admin: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public String promoteToAdmin(Long userId) {
        try {
            logger.debug("Promoting user {} to admin", userId);
            validateId(userId);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NoSuchElementException("User not found with ID: " + userId));

            if (user.getRoles().contains("ROLE_ADMIN")) {
                throw new IllegalArgumentException("User is already an admin");
            }

            user.addRole("ROLE_ADMIN");
            userRepository.save(user);

            logger.info("User {} promoted to admin successfully", userId);
            return "User promoted to admin successfully";
        } catch (IllegalArgumentException | NoSuchElementException e) {
            logger.error("Error promoting user to admin: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error promoting user: {}", e.getMessage());
            throw new RuntimeException("Failed to promote user", e);
        }
    }

    @Override
    @Transactional
    public String demoteFromAdmin(Long userId) {
        try {
            logger.debug("Demoting user {} from admin", userId);
            validateId(userId);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NoSuchElementException("User not found with ID: " + userId));

            if (!user.getRoles().contains("ROLE_ADMIN")) {
                throw new IllegalArgumentException("User is not an admin");
            }

            // Ensure user keeps at least ROLE_USER
            if (user.getRoles().size() == 1) {
                throw new IllegalArgumentException("Cannot remove the last role from user");
            }

            user.removeRole("ROLE_ADMIN");
            userRepository.save(user);

            logger.info("User {} demoted from admin successfully", userId);
            return "User demoted from admin successfully";
        } catch (IllegalArgumentException | NoSuchElementException e) {
            logger.error("Error demoting user from admin: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error demoting user: {}", e.getMessage());
            throw new RuntimeException("Failed to demote user", e);
        }
    }

    @Override
    @Transactional
    public String addRoleToUser(Long userId, String role) {
        try {
            logger.debug("Adding role {} to user {}", role, userId);
            validateId(userId);
            validateRole(role);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NoSuchElementException("User not found with ID: " + userId));

            if (user.getRoles().contains(role)) {
                throw new IllegalArgumentException("User already has role: " + role);
            }

            user.addRole(role);
            userRepository.save(user);

            logger.info("Role {} added to user {} successfully", role, userId);
            return "Role added successfully";
        } catch (IllegalArgumentException | NoSuchElementException e) {
            logger.error("Error adding role to user: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error adding role: {}", e.getMessage());
            throw new RuntimeException("Failed to add role", e);
        }
    }

    @Override
    @Transactional
    public String removeRoleFromUser(Long userId, String role) {
        try {
            logger.debug("Removing role {} from user {}", role, userId);
            validateId(userId);
            validateRole(role);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NoSuchElementException("User not found with ID: " + userId));

            if (!user.getRoles().contains(role)) {
                throw new IllegalArgumentException("User does not have role: " + role);
            }

            if (user.getRoles().size() == 1) {
                throw new IllegalArgumentException("Cannot remove the last role from user. User must have at least one role.");
            }

            user.removeRole(role);
            userRepository.save(user);

            logger.info("Role {} removed from user {} successfully", role, userId);
            return "Role removed successfully";
        } catch (IllegalArgumentException | NoSuchElementException e) {
            logger.error("Error removing role from user: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error removing role: {}", e.getMessage());
            throw new RuntimeException("Failed to remove role", e);
        }
    }

    @Override
    public String updateProfilePicture(Long userId, MultipartFile file)
    {
        try {
            validateId(userId);
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("File must not be null or empty");
            }
            String contentType = file.getContentType();
            if (contentType == null || !(contentType.equals("image/jpeg") || contentType.equals("image/png") || contentType.equals("image/webp"))) {
                throw new IllegalArgumentException("Only image/jpeg, image/png, or image/webp allowed");
            }

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NoSuchElementException("User not found with ID: " + userId));

            String ext = getExtension(file.getOriginalFilename());
            String objectName = "profile-pictures/user_" + userId + "_" + System.currentTimeMillis() + (ext.isEmpty() ? "" : "." + ext);

            BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName)
                    .setContentType(file.getContentType())
                    .build();
            storage.create(blobInfo, file.getBytes());

            String oldPathStr = user.getProfilePicture();
            if (oldPathStr != null && !oldPathStr.trim().isEmpty()) {
                try {
                    String prefix1 = "https://storage.googleapis.com/" + bucketName + "/";
                    String prefix2 = "https://" + bucketName + ".storage.googleapis.com/";
                    String oldObject = oldPathStr.startsWith(prefix1) ? oldPathStr.substring(prefix1.length())
                            : oldPathStr.startsWith(prefix2) ? oldPathStr.substring(prefix2.length())
                            : oldPathStr;
                    storage.delete(BlobId.of(bucketName, oldObject));
                } catch (Exception ex) {
                    logger.warn("Failed to delete old profile picture for user {}: {}", userId, ex.getMessage());
                }
            }

            String publicUrl = buildPublicUrl(objectName);
            user.setProfilePicture(publicUrl);
            userRepository.save(user);

            logger.info("Updated profile picture for user {} -> {}", userId, publicUrl);
            return publicUrl;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error updating profile picture for {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to update profile picture", e);
        }
    }

    private String getExtension(String name) {
        if (name == null) return "";
        int idx = name.lastIndexOf('.');
        return idx >= 0 ? name.substring(idx + 1).toLowerCase() : "";
    }

    @Override
    @Transactional
    public String updateUser(User user) {
        try {
            logger.debug("Updating user with ID: {}", user.getId());
            validateId(user.getId());

            User existingUser = userRepository.findById(user.getId())
                    .orElseThrow(() -> new NoSuchElementException("User not found with ID: " + user.getId()));

            if (user.getFirstName() != null && !user.getFirstName().trim().isEmpty()) {
                existingUser.setFirstName(user.getFirstName());
            }
            if (user.getLastName() != null && !user.getLastName().trim().isEmpty()) {
                existingUser.setLastName(user.getLastName());
            }
            if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
                User userWithEmail = userRepository.findByEmail(user.getEmail());
                if (userWithEmail != null && !userWithEmail.getId().equals(existingUser.getId())) {
                    throw new IllegalArgumentException("Email already exists for another user");
                }
                existingUser.setEmail(user.getEmail());
            }
            if (user.getCourse() != null) {
                existingUser.setCourse(user.getCourse());
            }
            if (user.getDepartment() != null) {
                existingUser.setDepartment(user.getDepartment());
            }
            if (user.getBio() != null) {
                existingUser.setBio(user.getBio());
            }
            if (user.getProfilePicture() != null) {
                existingUser.setProfilePicture(user.getProfilePicture());
            }


            if (user.getPassword() != null && !user.getPassword().trim().isEmpty()) {
                validatePassword(user.getPassword());
                existingUser.setPassword(passwordEncoder.encode(user.getPassword()));
            }

            userRepository.save(existingUser);
            logger.info("User with ID: {} updated successfully", user.getId());

            return "User updated successfully with ID " + user.getId();

        } catch (IllegalArgumentException | NoSuchElementException e) {
            logger.error("Error updating user: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error updating user: {}", e.getMessage());
            throw new RuntimeException("Failed to update user", e);
        }
    }

    @Override
    @Transactional
    public String deleteUser(Long userId) {
        try {
            logger.debug("Deleting user with Id: {}", userId);
            validateId(userId);

            if (!userRepository.existsById(userId)) {
                throw new IllegalArgumentException("User does not exist or is already deleted");
            }

            userRepository.deleteById(userId);
            logger.info("User with ID {} deleted successfully (soft delete)", userId);
            return "User deleted successfully";

        } catch (IllegalArgumentException e) {
            logger.error("Error deleting user {}: {}", userId, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error deleting user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to delete user", e);
        }
    }

    private void validateUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        if (!user.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new IllegalArgumentException("Invalid email format");
        }
        if ((user.getFirstName() == null || user.getFirstName().trim().isEmpty()) &&
                (user.getLastName() == null || user.getLastName().trim().isEmpty())) {
            throw new IllegalArgumentException("At least first name or last name must be provided");
        }

        validatePassword(user.getPassword());
    }

    private void validateId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be a positive number");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter");
        }
        if (!password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Password must contain at least one number");
        }
    }

    private void validateRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            throw new IllegalArgumentException("Role cannot be null or empty");
        }
        if (!role.startsWith("ROLE_")) {
            throw new IllegalArgumentException("Role must start with 'ROLE_' prefix (e.g., ROLE_ADMIN, ROLE_USER)");
        }
        if (!role.matches("^ROLE_[A-Z_]+$")) {
            throw new IllegalArgumentException("Role name must be in format: ROLE_UPPERCASE_WITH_UNDERSCORES");
        }
    }
}