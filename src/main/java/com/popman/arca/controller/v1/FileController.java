package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.file.FileUploadRequest;
import com.popman.arca.dto.v1.file.MultipleFileUploadRequest;
import com.popman.arca.entity.File;
import com.popman.arca.entity.UserPrincipal;
import com.popman.arca.service.FileService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService){
        this.fileService = fileService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MultipleFileUploadRequest> uploadFile(
            @RequestParam("file") List<MultipartFile> files,
            @RequestParam(value = "user_id", required = false) Long ignoredUserId,
            @RequestParam("post_id") Long postId,
            @AuthenticationPrincipal UserPrincipal userPrincipal) throws IOException {

        if (userPrincipal == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }

        List<File> savedFiles = fileService.uploadFilesV1(
                files,
                userPrincipal.getId(),
                postId,
                userPrincipal.hasRole("ROLE_ADMIN")
        );

        List<FileUploadRequest> fileResponses = savedFiles.stream()
                .map(savedFile -> new FileUploadRequest(
                        savedFile.getId(),
                        savedFile.getFileName(),
                        savedFile.getFileType(),
                        savedFile.getFileSize(),
                        savedFile.getUser().getId(),
                        savedFile.getPost().getId(),
                        "File uploaded successfully"
                )).collect(Collectors.toList());

        MultipleFileUploadRequest response = new MultipleFileUploadRequest(
                fileResponses,
                fileResponses.size() +  " files uploaded successfully"
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) throws IOException {
        Resource fileResource = fileService.downloadFileV1(id);
        File fileEntity = fileService.getFileV1(id).orElseThrow(() -> new IOException("File metadata not found"));
        String contentType = fileService.getFileContentTypeV1(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileEntity.getFileName() + "\"")
                .body(fileResource);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileUploadRequest> getFileInfo(@PathVariable Long id) {
        return fileService.getFileV1(id)
                .map(file -> {
                    FileUploadRequest response = new FileUploadRequest(
                            file.getId(),
                            file.getFileName(),
                            file.getFileType(),
                            file.getFileSize(),
                            file.getUser().getId(),
                            file.getPost().getId(),
                            "File info retrieved successfully"
                    );
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }



}
