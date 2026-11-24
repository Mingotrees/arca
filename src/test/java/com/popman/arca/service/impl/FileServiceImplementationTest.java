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
import org.springframework.web.server.ResponseStatusException;

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
        byte[] png = png();
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
    void validatesWholeBatchBeforeWritingAnything() throws Exception {
        byte[] png = png();
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
    void rejectsSignatureOnlyImageBeforeCreatingRowsOrFiles() throws Exception {
        prepareUpload();
        var upload = new MockMultipartFile("file", "fake.png", "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        assertThrows(IllegalArgumentException.class, () -> service.uploadFilesV1(List.of(upload), 7L, 12L, false));
        assertTrue(Files.notExists(uploadDir.resolve("posts")));
    }

    @Test
    void rejectsOversizedBytesAndOversizedBatchAtServiceBoundary() throws Exception {
        prepareUpload();
        byte[] large = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy("%PDF-1.7".getBytes(), 0, large, 0, 8);
        var upload = new MockMultipartFile("file", "large.pdf", "application/pdf", large);
        assertEquals(413, assertThrows(ResponseStatusException.class,
                () -> service.uploadFilesV1(List.of(upload), 7L, 12L, false)).getStatusCode().value());
        var part = new MockMultipartFile("file", "part.pdf", "application/pdf",
                java.util.Arrays.copyOf(large, 6 * 1024 * 1024));
        assertEquals(413, assertThrows(ResponseStatusException.class,
                () -> service.uploadFilesV1(List.of(part, part), 7L, 12L, false)).getStatusCode().value());
        assertTrue(Files.notExists(uploadDir.resolve("posts")));
    }

    @Test
    void rejectsSymlinkedPostDirectoryWithoutWritingOutsideRoot() throws Exception {
        prepareUpload();
        Path outside = Files.createDirectory(uploadDir.resolve("outside"));
        Path root = Files.createDirectory(uploadDir.resolve("storage"));
        Files.createSymbolicLink(root.resolve("posts"), outside);
        ReflectionTestUtils.setField(service, "uploadDir", root.toString());
        var upload = new MockMultipartFile("file", "test.pdf", "application/pdf", "%PDF-1.7".getBytes());
        assertThrows(java.io.IOException.class, () -> service.uploadFilesV1(List.of(upload), 7L, 12L, false));
        try (var paths = Files.list(outside)) { assertEquals(0, paths.count()); }
    }

    @Test
    void rejectsImagePixelBombBeforeDecoding() throws Exception {
        prepareUpload();
        byte[] bytes = png();
        java.nio.ByteBuffer.wrap(bytes).putInt(16, 100_000).putInt(20, 100_000);
        var upload = new MockMultipartFile("file", "bomb.png", "image/png", bytes);
        assertThrows(IllegalArgumentException.class, () -> service.uploadFilesV1(List.of(upload), 7L, 12L, false));
        assertTrue(Files.notExists(uploadDir.resolve("posts")));
    }

    private void prepareUpload() {
        when(postRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(post));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    }

    private byte[] png() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
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
