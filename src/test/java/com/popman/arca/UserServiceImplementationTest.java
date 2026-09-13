package com.popman.arca;

import com.popman.arca.dto.v1.user.UserUpdateRequest;
import com.popman.arca.entity.User;
import com.popman.arca.repository.UserRepository;
import com.popman.arca.service.impl.UserServiceImplementation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplementationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImplementation userService;

    @TempDir
    private Path uploadDir;

    @Test
    void normalUserCreationAlwaysHasExactlyUserRole() {
        User user = user("person@example.com", "RawPassword1");
        user.setRoles(Set.of("ROLE_ADMIN", "ROLE_USER"));

        when(userRepository.findByEmail(user.getEmail())).thenReturn(null);
        when(passwordEncoder.encode(user.getPassword())).thenReturn("encoded");
        when(userRepository.save(user)).thenAnswer(invocation -> {
            user.setId(7L);
            return user;
        });

        userService.createUserV1(user);

        assertEquals(Set.of("ROLE_USER"), user.getRoles());
        assertEquals("encoded", user.getPassword());
    }

    @Test
    void narrowUpdatePreservesSensitiveFields() {
        User existing = user("original@example.com", "encoded-password");
        existing.setId(9L);
        existing.setProfilePicture("profile-pictures/original.png");
        existing.setRoles(Set.of("ROLE_ADMIN", "ROLE_USER"));

        UserUpdateRequest request = new UserUpdateRequest();
        request.setFirstName("Updated");
        request.setBio("Updated bio");

        when(userRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        userService.updateUserV1(9L, request);

        assertEquals("Updated", existing.getFirstName());
        assertEquals("Updated bio", existing.getBio());
        assertEquals("original@example.com", existing.getEmail());
        assertEquals("encoded-password", existing.getPassword());
        assertEquals("profile-pictures/original.png", existing.getProfilePicture());
        assertEquals(Set.of("ROLE_ADMIN", "ROLE_USER"), existing.getRoles());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void profilePictureReadRejectsPathOutsideUploadRoot() {
        User user = user("person@example.com", "encoded-password");
        user.setId(12L);
        user.setProfilePicture("../outside.png");
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        ReflectionTestUtils.setField(userService, "uploadDir", "uploads");

        assertThrows(NoSuchElementException.class, () -> userService.getProfilePictureV1(12L));
    }

    @Test
    void profilePictureReadRejectsPostAttachmentWithinUploadRoot() throws Exception {
        Path attachment = uploadDir.resolve("posts/attachment.png");
        Files.createDirectories(attachment.getParent());
        Files.write(attachment, new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47});

        User user = user("person@example.com", "encoded-password");
        user.setId(12L);
        user.setProfilePicture("posts/attachment.png");
        when(userRepository.findById(12L)).thenReturn(Optional.of(user));
        ReflectionTestUtils.setField(userService, "uploadDir", uploadDir.toString());

        assertThrows(NoSuchElementException.class, () -> userService.getProfilePictureV1(12L));
    }

    @Test
    void profilePictureUploadRejectsSpoofedMediaType() {
        User user = user("person@example.com", "encoded-password");
        user.setId(12L);
        MockMultipartFile upload = new MockMultipartFile(
                "file", "avatar.png", "image/png", "not an image".getBytes());
        ReflectionTestUtils.setField(userService, "uploadDir", uploadDir.toString());

        assertThrows(IllegalArgumentException.class,
                () -> userService.updateProfilePictureV1(12L, upload));
        verify(userRepository, never()).save(any());
    }

    private User user(String email, String password) {
        User user = new User();
        user.setFirstName("Person");
        user.setEmail(email);
        user.setPassword(password);
        return user;
    }
}
