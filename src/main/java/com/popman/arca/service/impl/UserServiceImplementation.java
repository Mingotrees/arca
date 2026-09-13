package com.popman.arca.service.impl;

import com.popman.arca.dto.v1.user.ProfilePictureContent;
import com.popman.arca.dto.v1.user.UserUpdateRequest;
import com.popman.arca.entity.User;
import com.popman.arca.repository.UserRepository;
import com.popman.arca.service.UserService;
import jakarta.transaction.Transactional;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
public class UserServiceImplementation implements UserService {

  private static final Logger logger = LoggerFactory.getLogger(UserServiceImplementation.class);
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${file.upload-dir:uploads}")
  private String uploadDir;

  public UserServiceImplementation(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public User getUserV1(Long userId) {
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
  public List<User> getAllUserV1() {
    try {
      logger.debug("Fetching all users");
      return userRepository.findAll();
    } catch (Exception e) {
      logger.error("Error fetching all users: {}", e.getMessage());
      throw new RuntimeException("Failed to fetch users", e);
    }
  }

  @Override
  public List<User> searchUsersV1(String query) {
    if (query == null || query.trim().length() < 2) {
      throw new IllegalArgumentException("Search query must contain at least 2 characters");
    }
    String escaped = query.trim()
        .replace("!", "!!")
        .replace("%", "!%")
        .replace("_", "!_");
    return userRepository.search(escaped, PageRequest.of(0, 20));
  }

  @Override
  @Transactional
  public String createUserV1(User user) {
    try {
      logger.debug("Creating new user: {}", user.getEmail());
      validateUser(user);
      user.setId(null);

      if (userRepository.findByEmail(user.getEmail()) != null) {
        throw new IllegalArgumentException("User with email " + user.getEmail() + " already exists");
      }

      user.setPassword(passwordEncoder.encode(user.getPassword()));

      user.setRoles(new HashSet<>(Set.of("ROLE_USER")));
      logger.debug("Assigned ROLE_USER to new user");

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
  public String createAdminV1(User user) {
    try {
      logger.debug("Creating new admin user: {}", user.getEmail());
      validateUser(user);
      user.setId(null);

      if (userRepository.findByEmail(user.getEmail()) != null) {
        throw new IllegalArgumentException("User with email " + user.getEmail() + " already exists");
      }

      user.setPassword(passwordEncoder.encode(user.getPassword()));

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
  public String promoteToAdminV1(Long userId) {
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
  public String demoteFromAdminV1(Long userId) {
    try {
      logger.debug("Demoting user {} from admin", userId);
      validateId(userId);

      User user = userRepository.findById(userId)
          .orElseThrow(() -> new NoSuchElementException("User not found with ID: " + userId));

      if (!user.getRoles().contains("ROLE_ADMIN")) {
        throw new IllegalArgumentException("User is not an admin");
      }

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
  public String addRoleToUserV1(Long userId, String role) {
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
  public String removeRoleFromUserV1(Long userId, String role) {
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
  public String updateProfilePictureV1(Long userId, MultipartFile file) {
    try {
      validateId(userId);

      if (file == null || file.isEmpty()) {
        throw new IllegalArgumentException("File must not be null or empty");
      }

      String contentType = file.getContentType();
      if (contentType == null || !(contentType.equals("image/jpeg")
          || contentType.equals("image/png")
          || contentType.equals("image/webp"))) {
        throw new IllegalArgumentException("Only image/jpeg, image/png, or image/webp allowed");
      }
      byte[] bytes = file.getBytes();
      if (!hasImageSignature(bytes, contentType)) {
        throw new IllegalArgumentException("Profile picture content does not match its media type");
      }

      User user = userRepository.findById(userId)
          .orElseThrow(() -> new NoSuchElementException("User not found with ID: " + userId));

      String ext = extensionForContentType(contentType);
      String fileName = "user_" + userId + "_" + System.currentTimeMillis() + "." + ext;

      String relativePath = Paths.get("profile-pictures", fileName).toString();
      Path targetLocation = prepareProfilePictureTarget(fileName);

      Files.write(targetLocation, bytes);

      String oldPathStr = user.getProfilePicture();
      if (oldPathStr != null && !oldPathStr.trim().isEmpty()) {
        try {
          deleteStoredProfilePicture(oldPathStr);
        } catch (Exception ex) {
          logger.warn("Failed to delete old profile picture for user {}: {}", userId, ex.getMessage());
        }
      }

      user.setProfilePicture(relativePath);
      userRepository.save(user);

      logger.info("Updated profile picture for user {} -> {}", userId, relativePath);
      return relativePath;
    } catch (IllegalArgumentException | NoSuchElementException e) {
      throw e;
    } catch (IOException e) {
      logger.error("IO error updating profile picture for {}: {}", userId, e.getMessage());
      throw new RuntimeException("Failed to save profile picture", e);
    } catch (Exception e) {
      logger.error("Error updating profile picture for {}: {}", userId, e.getMessage());
      throw new RuntimeException("Failed to update profile picture", e);
    }
  }

  @Override
  public ProfilePictureContent getProfilePictureV1(Long userId) {
    validateId(userId);
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new NoSuchElementException("User not found with ID: " + userId));

    String storedPath = user.getProfilePicture();
    if (storedPath == null || storedPath.isBlank()) {
      throw new NoSuchElementException("Profile picture not found");
    }

    try {
      Path candidate = resolveStoredProfilePicture(storedPath);
      if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
        throw new NoSuchElementException("Profile picture not found");
      }
      String contentType = contentTypeForExtension(candidate);
      return new ProfilePictureContent(Files.readAllBytes(candidate), contentType);
    } catch (IOException | InvalidPathException | SecurityException e) {
      throw new NoSuchElementException("Profile picture not found", e);
    }
  }

  @Override
  @Transactional
  public String updateUserV1(Long userId, UserUpdateRequest request) {
    try {
      logger.debug("Updating user with ID: {}", userId);
      validateId(userId);

      User existingUser = userRepository.findById(userId)
          .orElseThrow(() -> new NoSuchElementException("User not found with ID: " + userId));

      if (request.getFirstName() != null && !request.getFirstName().trim().isEmpty()) {
        existingUser.setFirstName(request.getFirstName());
      }
      if (request.getLastName() != null && !request.getLastName().trim().isEmpty()) {
        existingUser.setLastName(request.getLastName());
      }
      if (request.getCourse() != null) {
        existingUser.setCourse(request.getCourse());
      }
      if (request.getDepartment() != null) {
        existingUser.setDepartment(request.getDepartment());
      }
      if (request.getBio() != null) {
        existingUser.setBio(request.getBio());
      }

      userRepository.save(existingUser);
      logger.info("User with ID: {} updated successfully", userId);

      return "User updated successfully with ID " + userId;

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
  public String deleteUserv1(Long userId) {
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

  private Path resolveUnderUploadRoot(String relativePath) {
    Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
    Path resolved = root.resolve(relativePath).normalize();
    if (!resolved.startsWith(root)) {
      throw new IllegalArgumentException("Invalid profile picture path");
    }
    return resolved;
  }

  private Path prepareProfilePictureTarget(String fileName) throws IOException {
    Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
    Files.createDirectories(root);
    Path realRoot = root.toRealPath();

    Path profileDirectory = root.resolve("profile-pictures");
    Files.createDirectories(profileDirectory);
    Path realProfileDirectory = profileDirectory.toRealPath();
    if (!realProfileDirectory.startsWith(realRoot)) {
      throw new IOException("Profile picture directory is outside upload root");
    }

    Path target = realProfileDirectory.resolve(fileName).normalize();
    if (!target.startsWith(realProfileDirectory)) {
      throw new IOException("Invalid profile picture target");
    }
    return target;
  }

  private void deleteStoredProfilePicture(String storedPath) throws IOException {
    Path candidate = resolveStoredProfilePicture(storedPath);
    if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    Files.deleteIfExists(candidate);
  }

  private Path resolveStoredProfilePicture(String storedPath) throws IOException {
    Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
    Path profileDirectory = root.resolve("profile-pictures").normalize();
    Path candidate = root.resolve(storedPath).normalize();
    if (!candidate.startsWith(profileDirectory)) {
      throw new IOException("Stored profile picture is outside profile picture directory");
    }

    Path realProfileDirectory = profileDirectory.toRealPath();
    Path realCandidate = candidate.toRealPath();
    if (!realCandidate.startsWith(realProfileDirectory)) {
      throw new IOException("Stored profile picture is outside profile picture directory");
    }
    return realCandidate;
  }

  private String extensionForContentType(String contentType) {
    return switch (contentType) {
      case "image/jpeg" -> "jpg";
      case "image/png" -> "png";
      case "image/webp" -> "webp";
      default -> throw new IllegalArgumentException("Unsupported image content type");
    };
  }

  private String contentTypeForExtension(Path path) {
    String name = path.getFileName().toString().toLowerCase();
    if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
      return "image/jpeg";
    }
    if (name.endsWith(".png")) {
      return "image/png";
    }
    if (name.endsWith(".webp")) {
      return "image/webp";
    }
    throw new NoSuchElementException("Profile picture not found");
  }

  private boolean hasImageSignature(byte[] bytes, String contentType) {
    return switch (contentType) {
      case "image/jpeg" -> bytes.length >= 3
          && (bytes[0] & 0xff) == 0xff
          && (bytes[1] & 0xff) == 0xd8
          && (bytes[2] & 0xff) == 0xff;
      case "image/png" -> startsWith(bytes, new byte[] {
          (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
      });
      case "image/webp" -> bytes.length >= 12
          && startsWith(bytes, new byte[] {0x52, 0x49, 0x46, 0x46})
          && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50;
      default -> false;
    };
  }

  private boolean startsWith(byte[] bytes, byte[] prefix) {
    if (bytes.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (bytes[i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }
}
