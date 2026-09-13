package com.popman.arca.dto.v1.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.popman.arca.entity.BannedEmail;

import java.time.LocalDateTime;

public record BannedEmailResponse(
        Long id,
        String email,
        String reason,
        @JsonProperty("created_at") LocalDateTime createdAt
) {
    public BannedEmailResponse(BannedEmail bannedEmail) {
        this(bannedEmail.getId(), bannedEmail.getEmail(), bannedEmail.getReason(), bannedEmail.getCreatedAt());
    }
}
