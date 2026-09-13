package com.popman.arca.service;

import com.popman.arca.entity.File;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface FileService {
    List<File> uploadFilesV1(List<MultipartFile> files, Long actorId, Long postId, boolean actorIsAdmin)
            throws IOException;
    Resource downloadFileV1(Long id) throws IOException;
    Optional<File> getFileV1(Long id);
    String getFileContentTypeV1(Long id) throws IOException;
}
