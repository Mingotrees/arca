package com.popman.arca.service.impl;

import com.popman.arca.entity.File;
import com.popman.arca.entity.Post;
import com.popman.arca.entity.User;
import com.popman.arca.repository.FileRepository;
import com.popman.arca.repository.PostRepository;
import com.popman.arca.repository.UserRepository;
import com.popman.arca.service.FileService;
import com.popman.arca.service.PostAccessPolicy;
import com.popman.arca.service.media.UploadValidator;
import com.popman.arca.service.media.UploadValidator.ValidatedUpload;
import com.popman.arca.service.media.MediaStorage;
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
  private final PostAccessPolicy accessPolicy = new PostAccessPolicy();
  private final UploadValidator validator = new UploadValidator();
  private final MediaStorage storage = new MediaStorage();

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
    postRepository.lockLogicalPostByRowId(postId);
    Post post = postRepository.findByIdForUpdate(postId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
    accessPolicy.requireMutable(post, actorId, actorIsAdmin);

    User user = userRepository.findById(actorId)
        .orElseThrow(() -> new NoSuchElementException("User not found with id " + actorId));
    if (fileRepository.countByPostId(postId) + files.size() > MAX_FILES_PER_POST) {
      throw new IllegalArgumentException("Only maximum of 3 files allowed per post");
    }

    List<ValidatedUpload> candidates = new ArrayList<>(files.size());
    long totalBytes = 0;
    for (MultipartFile file : files) {
      ValidatedUpload candidate = validator.validate(file, false);
      totalBytes += candidate.bytes().length;
      if (totalBytes > UploadValidator.MAX_BYTES) throw UploadValidator.tooLarge();
      candidates.add(candidate);
    }

    List<Path> writtenPaths = new ArrayList<>();
    List<File> entities = new ArrayList<>(candidates.size());
    try {
      for (ValidatedUpload candidate : candidates) {
        Path targetLocation = storage.destination(uploadDir, "posts/" + postId, UUID.randomUUID() + candidate.extension());

        writtenPaths.add(targetLocation);
        Files.write(targetLocation, candidate.bytes(), java.nio.file.StandardOpenOption.CREATE_NEW);

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
    File fileEntity = getFileV1(id)
        .orElseThrow(() -> new NoSuchElementException("File not found"));

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
    File fileEntity = getFileV1(id)
        .orElseThrow(() -> new NoSuchElementException("File not found"));

    Path path = resolveStoredPath(fileEntity);

    if (!Files.exists(path)) {
      throw new NoSuchElementException("File not found in local storage");
    }

    return switch (fileEntity.getFileType() == null ? "" : fileEntity.getFileType()) {
      case "image/jpeg", "image/png", "application/pdf" -> fileEntity.getFileType();
      default -> "application/octet-stream";
    };
  }

  @Override
  public Optional<File> getFileV1(Long id) {
    return fileRepository.findByIdAndPostIsDeletedFalse(id)
        .filter(file -> accessPolicy.canRead(file.getPost()));
  }

  private Path resolveStoredPath(File fileEntity) throws IOException {
    try {
      // Legacy paths were relative to the process working directory, not the upload root.
      return storage.existing(uploadDir, Paths.get(fileEntity.getFilePath()).toAbsolutePath().toString(), "posts");
    } catch (IOException | java.nio.file.InvalidPathException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
    }
  }
}
