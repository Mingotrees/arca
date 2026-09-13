package com.popman.arca.dto.v1.file;

public class FileUploadRequest {
    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Long userId;
    private Long postId;
    private String message;

    public FileUploadRequest() {}

    public FileUploadRequest(Long id, String fileName, String fileType, Long fileSize,
                             Long userId, Long postId, String message) {
        this.id = id;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.userId = userId;
        this.postId = postId;
        this.message = message;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
