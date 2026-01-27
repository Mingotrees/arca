package com.popman.arca.dto.v1.file;

import java.util.List;

public class MultipleFileUploadRequest {
    private List<FileUploadRequest> files;
    private String message;

    public MultipleFileUploadRequest(List<FileUploadRequest> files, String message) {
        this.files = files;
        this.message = message;
    }

    public List<FileUploadRequest> getFiles() {
        return files;
    }

    public void setFiles(List<FileUploadRequest> files) {
        this.files = files;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
