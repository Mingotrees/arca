package com.popman.arca.dto.file;

import java.util.List;

public class MultipleFileUploadResponse {
    private List<FileUploadResponse> files;
    private String message;

    public MultipleFileUploadResponse(List<FileUploadResponse> files, String message) {
        this.files = files;
        this.message = message;
    }

    public List<FileUploadResponse> getFiles() {
        return files;
    }

    public void setFiles(List<FileUploadResponse> files) {
        this.files = files;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
