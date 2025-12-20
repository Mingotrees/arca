package com.popman.arca.service.impl;


import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

@Service
public class FileServiceImplementation implements FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileServiceImplementation.class);
    private final FileRepository fileRepository;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PostRepository postRepository;

    private final Storage storage;

    @Value("${spring.cloud.gcp.storage.bucket:arca-uploads}")
    private String bucketName;

    public FileServiceImplementation(FileRepository fileRepository, Storage storage){
        this.fileRepository = fileRepository;
        this.storage = storage;
    }

    private String buildPublicUrl(String objectName) {
        return "https://storage.googleapis.com/" + bucketName + "/" + objectName;
    }


    @Override
    public File uploadFile(MultipartFile file, Long userId, Long postId) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id " + userId));
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found with id " + postId));

        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String fileName = System.currentTimeMillis() + "_" + original;
        String objectName = "posts/" + postId + "/" + fileName;

        File fileEntity = new File();
        fileEntity.setFileName(fileName);
        fileEntity.setFilePath(buildPublicUrl(objectName));
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
                BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName)
                    .setContentType(file.getContentType())
                    .build();
            storage.create(blobInfo, file.getBytes());
        } catch (IOException e) {
            fileRepository.delete(savedFile);
            throw new IOException("Failed to write file to cloud storage. Database record rolled back.", e);
        }

        return savedFile;
    }

    @Override
    public Resource downloadFile(Long id) throws IOException {

        File fileEntity = fileRepository.findById(id)
                .orElseThrow(() -> new IOException("File not found with id" + id));

        String urlOrObjectName = fileEntity.getFilePath();
        String prefix1 = "https://storage.googleapis.com/" + bucketName + "/";
        String prefix2 = "https://" + bucketName + ".storage.googleapis.com/";
        String objectName = urlOrObjectName.startsWith(prefix1) ? urlOrObjectName.substring(prefix1.length())
                : urlOrObjectName.startsWith(prefix2) ? urlOrObjectName.substring(prefix2.length())
                : urlOrObjectName;

        Blob blob = storage.get(bucketName, objectName);
        if (blob == null || !blob.exists()) {
            throw new IOException("File not found in cloud storage: " + objectName);
        }

        byte[] content = blob.getContent();
        return new ByteArrayResource(content);
    }

    @Override
    public String getFileContentType(Long id) throws IOException {
        File fileEntity = fileRepository.findById(id)
                .orElseThrow(() -> new IOException("File not found with id" + id));

        String urlOrObjectName = fileEntity.getFilePath();
        String prefix1 = "https://storage.googleapis.com/" + bucketName + "/";
        String prefix2 = "https://" + bucketName + ".storage.googleapis.com/";
        String objectName = urlOrObjectName.startsWith(prefix1) ? urlOrObjectName.substring(prefix1.length())
                : urlOrObjectName.startsWith(prefix2) ? urlOrObjectName.substring(prefix2.length())
                : urlOrObjectName;

        Blob blob = storage.get(bucketName, objectName);
        if (blob == null || !blob.exists()) {
            throw new IOException("File not found in cloud storage: " + objectName);
        }
        String ct = blob.getContentType();
        return ct != null ? ct : "application/octet-stream";
    }

    @Override
    public Optional<File> getFile(Long id) {
        return fileRepository.findById(id);
    }
}


