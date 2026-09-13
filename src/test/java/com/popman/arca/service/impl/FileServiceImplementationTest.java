package com.popman.arca.service.impl;

import com.popman.arca.entity.File;
import com.popman.arca.entity.Post;
import com.popman.arca.entity.User;
import com.popman.arca.repository.FileRepository;
import com.popman.arca.repository.PostRepository;
import com.popman.arca.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceImplementationTest {

    @Mock
    private FileRepository fileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PostRepository postRepository;
    @TempDir
    private Path uploadDir;

    private FileServiceImplementation service;
    private Post post;
    private User user;

    @BeforeEach
    void setUp() {
        service = new FileServiceImplementation(fileRepository, userRepository, postRepository);
        ReflectionTestUtils.setField(service, "uploadDir", uploadDir.toString());

        post = new Post();
        post.setId(12L);
        post.setUserId(7L);
        user = new User();
        user.setId(7L);
    }

    @Test
    void acceptsValidBytesWithNullMimeAndUsesContainedUuidPath() throws Exception {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x01};
        MockMultipartFile upload = new MockMultipartFile("file", "../my image.png", null, png);
        when(postRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(post));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(fileRepository.countByPostId(12L)).thenReturn(0L);
        when(fileRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<File> saved = service.uploadFilesV1(List.of(upload), 7L, 12L, false);

        File file = saved.getFirst();
        Path storedPath = Path.of(file.getFilePath());
        assertEquals("my_image.png", file.getFileName());
        assertEquals("image/png", file.getFileType());
        assertTrue(storedPath.startsWith(uploadDir.toAbsolutePath().normalize()));
        assertTrue(storedPath.getFileName().toString()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png"));
        assertTrue(Files.exists(storedPath));
    }

    @Test
    void validatesWholeBatchBeforeWritingAnything() {
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        MockMultipartFile valid = new MockMultipartFile("file", "valid.png", "image/png", png);
        MockMultipartFile mismatch = new MockMultipartFile(
                "file", "invalid.pdf", "application/pdf", png);
        when(postRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(post));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(fileRepository.countByPostId(12L)).thenReturn(0L);

        assertThrows(IllegalArgumentException.class,
                () -> service.uploadFilesV1(List.of(valid, mismatch), 7L, 12L, false));

        verify(fileRepository, never()).saveAllAndFlush(anyList());
        assertTrue(Files.notExists(uploadDir.resolve("posts")));
    }

    @Test
    void rejectsUploadWhenPostAlreadyHasThreeFiles() {
        byte[] pdf = {0x25, 0x50, 0x44, 0x46, 0x2d};
        MockMultipartFile upload = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", pdf);
        when(postRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(post));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(fileRepository.countByPostId(12L)).thenReturn(3L);

        assertThrows(IllegalArgumentException.class,
                () -> service.uploadFilesV1(List.of(upload), 7L, 12L, false));

        verify(fileRepository, never()).saveAllAndFlush(anyList());
    }
}
