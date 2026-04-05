package com.popman.arca.service.impl;

import com.popman.arca.entity.File;
import com.popman.arca.entity.Post;
import com.popman.arca.entity.User;
import com.popman.arca.repository.FileRepository;
import com.popman.arca.repository.PostRepository;
import com.popman.arca.repository.UserRepository;
import com.popman.arca.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

@Service
public class FileServiceImplementation implements FileService {

  private static final Logger logger = LoggerFactory.getLogger(FileServiceImplementation.class);

  private final FileRepository fileRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PostRepository postRepository;

  @Value("${file.upload-dir:uploads}")
  private String uploadDir;

  public FileServiceImplementation(FileRepository fileRepository) {
    this.fileRepository = fileRepository;
  }

  @Override
  public File uploadFileV1(MultipartFile file, Long userId, Long postId) throws IOException {
    if (file.isEmpty()) {
      throw new IOException("Cannot upload an empty file");
    }
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new RuntimeException("User not found with id " + userId));

    Post post = postRepository.findById(postId)
        .orElseThrow(() -> new RuntimeException("Post not found with id " + postId));

    String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
    String fileName = System.currentTimeMillis() + "_" + original;

    Path postFolder = Paths.get(uploadDir, "posts", String.valueOf(postId)).toAbsolutePath().normalize();
    Files.createDirectories(postFolder);

    Path targetLocation = postFolder.resolve(fileName);

    try {
      Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new IOException("Failed to save file to local storage", e);
    }

    File fileEntity = new File();
    fileEntity.setFileName(fileName);
    fileEntity.setFilePath(targetLocation.toString()); // store actual local path
    fileEntity.setFileType(file.getContentType());
    fileEntity.setFileSize(file.getSize());
    fileEntity.setPost(post);
    fileEntity.setUser(user);

    try {
      return fileRepository.save(fileEntity);
    } catch (RuntimeException e) {
      Files.deleteIfExists(targetLocation); // rollback file if DB save fails
      throw new RuntimeException("Database save failed: " + e.getMessage(), e);
    }
  }

  @Override
  public Resource downloadFileV1(Long id) throws IOException {
    File fileEntity = fileRepository.findById(id)
        .orElseThrow(() -> new IOException("File not found with id " + id));

    try {
      Path path = Paths.get(fileEntity.getFilePath()).toAbsolutePath().normalize();
      Resource resource = new UrlResource(path.toUri());

      if (!resource.exists() || !resource.isReadable()) {
        throw new IOException("File not found or not readable: " + fileEntity.getFilePath());
      }

      return resource;
    } catch (MalformedURLException e) {
      throw new IOException("Invalid file path: " + fileEntity.getFilePath(), e);
    }
  }

  @Override
  public String getFileContentTypeV1(Long id) throws IOException {
    File fileEntity = fileRepository.findById(id)
        .orElseThrow(() -> new IOException("File not found with id " + id));

    Path path = Paths.get(fileEntity.getFilePath()).toAbsolutePath().normalize();

    if (!Files.exists(path)) {
      throw new IOException("File not found in local storage: " + fileEntity.getFilePath());
    }

    String contentType = Files.probeContentType(path);
    return contentType != null ? contentType : "application/octet-stream";
  }

  @Override
  public Optional<File> getFileV1(Long id) {
    return fileRepository.findById(id);
  }
}
