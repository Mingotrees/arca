package com.popman.arca.controller;

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

    @PostMapping("/upload")
    public ResponseEntity<File> uploadFile(@RequestParam("file")MultipartFile file, @RequestParam("userId")Long userId, @RequestParam("postId")Long postId) throws IOException {
        File savedFile = fileService.uploadFile(file, userId, postId);

        return ResponseEntity.ok(savedFile);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) throws IOException {
        Resource fileResource = fileService.downloadFile(id);
        File fileEntity = fileService.getFile(id).orElseThrow(()-> new IOException("File metadata not found"));

        return ResponseEntity.ok().contentType(MediaType.parseMediaType(fileEntity.getFileType())).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"" + fileEntity.getFileName() + "\"").body(fileResource);
    }
    @GetMapping("/{id}")
    public ResponseEntity<File> getFileInfo(@PathVariable Long id){
        return fileService.getFile(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

}
