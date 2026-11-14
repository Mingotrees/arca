package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.file.FileUploadRequest;
import com.popman.arca.dto.v1.file.MultipleFileUploadRequest;
import com.popman.arca.entity.File;
import com.popman.arca.entity.UserPrincipal;
import com.popman.arca.service.FileService;
import com.popman.arca.service.media.UploadValidator;
import org.springframework.http.ContentDisposition;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
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
@ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService){
        this.fileService = fileService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(description = "Owner/admin upload to a post version row. JPEG, PNG or PDF only; at most three files per version, "
            + "10 MiB per file and multipart request, and 25000000 decoded pixels per image. Extension, declared MIME and content must agree.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = MultipleFileUploadRequest.class)))
    @ApiResponse(responseCode = "400", description = "Invalid, empty or mismatched upload", content = @Content)
    @ApiResponse(responseCode = "403", description = "Visible post may only be modified by owner/admin", content = @Content)
    @ApiResponse(responseCode = "404", description = "Post missing, deleted or inaccessible", content = @Content)
    @ApiResponse(responseCode = "413", description = "Upload exceeds 10 MiB limit", content = @Content)
    @ApiResponse(responseCode = "415", description = "Unsupported media type or file extension", content = @Content)
    public ResponseEntity<MultipleFileUploadRequest> uploadFile(
            @RequestParam("file") List<MultipartFile> files,
            @Parameter(deprecated = true, description = "Ignored; acting user is the authenticated JWT principal")
            @RequestParam(value = "user_id", required = false) Long ignoredUserId,
            @Parameter(description = "Post version database row id, not logical post_id") @RequestParam("post_id") Long postId,
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
    @ApiResponse(responseCode = "200", description = "Attachment from a readable parent post",
            content = @Content(schema = @Schema(type = "string", format = "binary")), headers = {
            @Header(name = "Content-Disposition", description = "attachment with sanitized filename", schema = @Schema(type = "string")),
            @Header(name = "Cache-Control", schema = @Schema(type = "string", allowableValues = "no-store")),
            @Header(name = "X-Content-Type-Options", schema = @Schema(type = "string", allowableValues = "nosniff"))})
    @ApiResponse(responseCode = "404", description = "File missing or parent post deleted/inaccessible", content = @Content)
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) throws IOException {
        Resource fileResource = fileService.downloadFileV1(id);
        File fileEntity = fileService.getFileV1(id).orElseThrow(() -> new IOException("File metadata not found"));
        String contentType = fileService.getFileContentTypeV1(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(UploadValidator.safeFilename(fileEntity.getFileName())).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(fileResource);
    }

    @GetMapping("/{id}")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = FileUploadRequest.class)))
    @ApiResponse(responseCode = "404", description = "File missing or parent post deleted/inaccessible", content = @Content)
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
                .orElseThrow(() -> new java.util.NoSuchElementException("File not found"));
    }



}
