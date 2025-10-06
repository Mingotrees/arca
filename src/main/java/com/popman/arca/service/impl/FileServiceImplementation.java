package com.popman.arca.service.impl;


import com.popman.arca.entity.File;
import com.popman.arca.repository.FileRepository;
import com.popman.arca.service.FileService;
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

    private final FileRepository fileRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public FileServiceImplementation(FileRepository fileRepository){
        this.fileRepository = fileRepository;
    }


    @Override
    public File uploadFile(MultipartFile file, Long userId, Long postId) throws IOException {
        Files.createDirectories(Paths.get(uploadDir));


        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path filePath = Paths.get(uploadDir, fileName);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        File fileEntity = new File();

        fileEntity.setFileName(fileName);
        fileEntity.setFilePath(filePath.toString());
        fileEntity.setFileType(file.getContentType());
        fileEntity.setFileSize(file.getSize());


        return fileRepository.save(fileEntity);
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
