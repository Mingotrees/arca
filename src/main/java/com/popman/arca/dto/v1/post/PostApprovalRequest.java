package com.popman.arca.dto.v1.post;

import jakarta.validation.constraints.NotNull;

public class PostApprovalRequest {
    @NotNull(message = "Approval decision is required")
    private Boolean approved;

    private String rejectionReason;

    public PostApprovalRequest() {}

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}
