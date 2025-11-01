package com.popman.arca.controller;

import com.popman.arca.dto.file.FileUploadResponse;
import com.popman.arca.entity.File;
import com.popman.arca.service.FileService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/files")
public class FileController {


    private final FileService fileService;

    public FileController(FileService fileService){
        this.fileService = fileService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("user_id") Long userId,
            @RequestParam("post_id") Long postId) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be null or empty");
        }

        if (userId == null || postId == null) {
            throw new IllegalArgumentException("userId and postId must not be null");
        }

        System.out.println("userId=" + userId + ", postId=" + postId);

        File savedFile = fileService.uploadFile(file, userId, postId);

        FileUploadResponse response = new FileUploadResponse(
                savedFile.getId(),
                savedFile.getFileName(),
                savedFile.getFileType(),
                savedFile.getFileSize(),
                savedFile.getFilePath(),
                savedFile.getUser().getId(),
                savedFile.getPost().getId(),
                "File uploaded successfully"
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) throws IOException {
        Resource fileResource = fileService.downloadFile(id);
        File fileEntity = fileService.getFile(id).orElseThrow(()-> new IOException("File metadata not found"));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fileEntity.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"" + fileEntity.getFileName() + "\"").body(fileResource);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileUploadResponse> getFileInfo(@PathVariable Long id) {
        return fileService.getFile(id)
                .map(file -> {
                    FileUploadResponse response = new FileUploadResponse(
                            file.getId(),
                            file.getFileName(),
                            file.getFileType(),
                            file.getFileSize(),
                            file.getFilePath(),
                            file.getUser().getId(),
                            file.getPost().getId(),
                            "File info retrieved successfully"
                    );
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

}
