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
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;

@Service
public class FileServiceImplementation implements FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileServiceImplementation.class);
    private final FileRepository fileRepository;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PostRepository postRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public FileServiceImplementation(FileRepository fileRepository){
        this.fileRepository = fileRepository;
    }


    @Override
    public File uploadFile(MultipartFile file, Long userId, Long postId) throws IOException {
        Files.createDirectories(Paths.get(uploadDir));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id " + userId));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found with id " + postId));

        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path filePath = Paths.get(uploadDir, fileName);

        File fileEntity = new File();
        fileEntity.setFileName(fileName);
        fileEntity.setFilePath(filePath.toString());
        fileEntity.setFileType(file.getContentType());
        fileEntity.setFileSize(file.getSize());
        fileEntity.setPost(post);
        fileEntity.setUser(user);

        File savedFile;
        try {
            savedFile = fileRepository.save(fileEntity);
        } catch (RuntimeException e) {
            throw new RuntimeException("Database save failed: " + e.getMessage(), e);
        }

        try {
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            fileRepository.delete(savedFile);
            throw new IOException("Failed to write file to storage. Database record rolled back.", e);
        }

        return savedFile;
    }

    @Override
    public Resource downloadFile(Long id) throws IOException {

        File fileEntity = fileRepository.findById(id).orElseThrow(()-> new IOException("File not found with id" + id));

        Path filePath = Paths.get(fileEntity.getFilePath());

        if(!Files.exists(filePath)){
            throw new IOException("File not Found on disk" + filePath);
        }

        return new FileSystemResource(filePath);
    }

    @Override
    public Optional<File> getFile(Long id) {
        return fileRepository.findById(id);
    }
}


