package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.file.FileUploadRequest;
import com.popman.arca.dto.v1.file.MultipleFileUploadRequest;
import com.popman.arca.entity.File;
import com.popman.arca.service.FileService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
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
            @RequestParam("user_id") Long userId,
            @RequestParam("post_id") Long postId) throws IOException {

        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("File must not be null or empty");
        }

        if (files.size() > 3){
            throw new IllegalArgumentException("Only maximum of 3 files allowed");
        }

        if (userId == null || postId == null) {
            throw new IllegalArgumentException("userId and postId must not be null");
        }

        List<File> savedFiles = new ArrayList<>();

        for(MultipartFile file: files){
            String contentType = file.getContentType();
            if(contentType.equals("image/jpeg") ||
                    contentType.equals("image/png") ||
                    contentType.equals("application/pdf")){
                File savedFile = fileService.uploadFileV1(file, userId, postId);
                savedFiles.add(savedFile);
            }else{
                throw new IllegalArgumentException("Only image/jpeg or image/png or application/pdf");
            }

        }

        List<FileUploadRequest> fileResponses = savedFiles.stream()
                .map(savedFile -> new FileUploadRequest(
                        savedFile.getId(),
                        savedFile.getFileName(),
                        savedFile.getFileType(),
                        savedFile.getFileSize(),
                        savedFile.getFilePath(),
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
