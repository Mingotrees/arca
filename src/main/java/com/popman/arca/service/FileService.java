package com.popman.arca.service;

import com.popman.arca.entity.File;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

public interface FileService {
    File uploadFile(MultipartFile file, Long userId, Long postId) throws IOException;
    Resource downloadFile(Long id) throws IOException;
    Optional<File> getFile(Long id);
    String getFileContentType(Long id) throws IOException;
}
