package com.popman.arca.service.impl;

import com.popman.arca.entity.File;
import com.popman.arca.entity.Post;
import com.popman.arca.entity.User;
import com.popman.arca.repository.FileRepository;
import com.popman.arca.repository.PostRepository;
import com.popman.arca.repository.UserRepository;
import com.popman.arca.service.FileService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class FileServiceImplementation implements FileService {

  private static final Logger logger = LoggerFactory.getLogger(FileServiceImplementation.class);
  private static final int MAX_FILES_PER_POST = 3;

  private final FileRepository fileRepository;
  private final UserRepository userRepository;
  private final PostRepository postRepository;

  @Value("${file.upload-dir:uploads}")
  private String uploadDir;

  public FileServiceImplementation(FileRepository fileRepository, UserRepository userRepository,
      PostRepository postRepository) {
    this.fileRepository = fileRepository;
    this.userRepository = userRepository;
    this.postRepository = postRepository;
  }

  @Override
  @Transactional(rollbackOn = Exception.class)
  public List<File> uploadFilesV1(List<MultipartFile> files, Long actorId, Long postId,
      boolean actorIsAdmin) throws IOException {
    if (files == null || files.isEmpty()) {
      throw new IllegalArgumentException("At least one file is required");
    }
    if (actorId == null || postId == null) {
      throw new IllegalArgumentException("actorId and postId must not be null");
    }
    Post post = postRepository.findByIdForUpdate(postId)
        .orElseThrow(() -> new NoSuchElementException("Post not found with id " + postId));
    if (!actorIsAdmin && !actorId.equals(post.getUserId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only post owner or admin can upload files");
    }

    User user = userRepository.findById(actorId)
        .orElseThrow(() -> new NoSuchElementException("User not found with id " + actorId));
    if (fileRepository.countByPostId(postId) + files.size() > MAX_FILES_PER_POST) {
      throw new IllegalArgumentException("Only maximum of 3 files allowed per post");
    }

    List<UploadCandidate> candidates = new ArrayList<>(files.size());
    for (MultipartFile file : files) {
      candidates.add(validate(file));
    }

    Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    Path postFolder = uploadRoot.resolve("posts").resolve(String.valueOf(postId)).normalize();
    if (!postFolder.startsWith(uploadRoot)) {
      throw new IOException("Invalid upload destination");
    }

    List<Path> writtenPaths = new ArrayList<>();
    List<File> entities = new ArrayList<>(candidates.size());
    try {
      Files.createDirectories(postFolder);
      for (UploadCandidate candidate : candidates) {
        Path targetLocation = postFolder.resolve(UUID.randomUUID() + candidate.extension()).normalize();
        if (!targetLocation.startsWith(postFolder)) {
          throw new IOException("Invalid upload destination");
        }

        writtenPaths.add(targetLocation);
        Files.write(targetLocation, candidate.bytes());

        File fileEntity = new File();
        fileEntity.setFileName(candidate.fileName());
        fileEntity.setFilePath(targetLocation.toString());
        fileEntity.setFileType(candidate.contentType());
        fileEntity.setFileSize((long) candidate.bytes().length);
        fileEntity.setPost(post);
        fileEntity.setUser(user);
        entities.add(fileEntity);
      }
      registerRollbackCleanup(writtenPaths);
      return fileRepository.saveAllAndFlush(entities);
    } catch (IOException | RuntimeException e) {
      cleanupFiles(writtenPaths);
      throw e;
    }
  }

  private UploadCandidate validate(MultipartFile file) throws IOException {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("Files must not be null or empty");
    }

    String originalName = file.getOriginalFilename();
    if (originalName == null || originalName.isBlank()) {
      throw new IllegalArgumentException("File name is required");
    }
    String basename = originalName.replace('\\', '/');
    basename = basename.substring(basename.lastIndexOf('/') + 1)
        .replaceAll("[\\p{Cntrl}]", "")
        .replaceAll("[^A-Za-z0-9._-]", "_")
        .replaceFirst("^\\.+", "");
    if (basename.isBlank()) {
      throw new IllegalArgumentException("Invalid file name");
    }

    int extensionIndex = basename.lastIndexOf('.');
    if (extensionIndex <= 0 || extensionIndex == basename.length() - 1) {
      throw new IllegalArgumentException("File must have a JPEG, PNG, or PDF extension");
    }
    String extension = basename.substring(extensionIndex).toLowerCase(Locale.ROOT);
    String expectedType = switch (extension) {
      case ".jpg", ".jpeg" -> "image/jpeg";
      case ".png" -> "image/png";
      case ".pdf" -> "application/pdf";
      default -> throw new IllegalArgumentException("Only JPEG, PNG, or PDF files are allowed");
    };

    byte[] bytes = file.getBytes();
    if (!expectedType.equals(detectContentType(bytes))) {
      throw new IllegalArgumentException("File extension does not match file content");
    }
    String declaredType = file.getContentType();
    if (declaredType != null && !declaredType.isBlank()
        && !expectedType.equalsIgnoreCase(declaredType.trim())) {
      throw new IllegalArgumentException("Declared content type does not match file content");
    }
    return new UploadCandidate(basename, extension, expectedType, bytes);
  }

  private String detectContentType(byte[] bytes) {
    if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
        && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
      return "image/jpeg";
    }
    byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    if (startsWith(bytes, png)) {
      return "image/png";
    }
    byte[] pdf = {0x25, 0x50, 0x44, 0x46, 0x2d};
    return startsWith(bytes, pdf) ? "application/pdf" : null;
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

  private void cleanupFiles(List<Path> paths) {
    for (Path path : paths) {
      try {
        Files.deleteIfExists(path);
      } catch (IOException cleanupError) {
        logger.warn("Failed to clean up uploaded file {}", path, cleanupError);
      }
    }
  }

  private void registerRollbackCleanup(List<Path> paths) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    List<Path> rollbackPaths = List.copyOf(paths);
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status != TransactionSynchronization.STATUS_COMMITTED) {
          cleanupFiles(rollbackPaths);
        }
      }
    });
  }

  @Override
  public Resource downloadFileV1(Long id) throws IOException {
    File fileEntity = fileRepository.findById(id)
        .orElseThrow(() -> new NoSuchElementException("File not found with id " + id));

    try {
      Path path = resolveStoredPath(fileEntity);
      Resource resource = new UrlResource(path.toUri());

      if (!resource.exists() || !resource.isReadable()) {
        throw new NoSuchElementException("File not found or not readable");
      }

      return resource;
    } catch (MalformedURLException e) {
      throw new IOException("Invalid file path: " + fileEntity.getFilePath(), e);
    }
  }

  @Override
  public String getFileContentTypeV1(Long id) throws IOException {
    File fileEntity = fileRepository.findById(id)
        .orElseThrow(() -> new NoSuchElementException("File not found with id " + id));

    Path path = resolveStoredPath(fileEntity);

    if (!Files.exists(path)) {
      throw new NoSuchElementException("File not found in local storage");
    }

    String contentType = Files.probeContentType(path);
    return contentType != null ? contentType : "application/octet-stream";
  }

  @Override
  public Optional<File> getFileV1(Long id) {
    return fileRepository.findById(id);
  }

  private Path resolveStoredPath(File fileEntity) throws IOException {
    Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    Path path = Paths.get(fileEntity.getFilePath()).toAbsolutePath().normalize();
    if (!path.startsWith(uploadRoot)) {
      throw new IOException("Invalid file storage path");
    }
    return path;
  }

  private record UploadCandidate(String fileName, String extension, String contentType, byte[] bytes) {}
}
