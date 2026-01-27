package com.popman.arca.service;

import com.popman.arca.entity.File;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

public interface FileService {
    File uploadFileV1(MultipartFile file, Long userId, Long postId) throws IOException;
    Resource downloadFileV1(Long id) throws IOException;
    Optional<File> getFileV1(Long id);
    String getFileContentTypeV1(Long id) throws IOException;
}
