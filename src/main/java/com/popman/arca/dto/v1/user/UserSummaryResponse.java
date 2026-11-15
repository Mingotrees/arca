package com.popman.arca.dto.v1.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.popman.arca.entity.User;

public record UserSummaryResponse(
        Long id,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        String course,
        String department,
        String bio,
        @JsonProperty("profile_picture") String profilePicture,
        @JsonProperty("created_at") @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        @io.swagger.v3.oas.annotations.media.Schema(accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY,
                description = "UTC creation time; deployment-time approximation for legacy users") java.time.Instant createdAt,
        @JsonProperty("updated_at") @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        @io.swagger.v3.oas.annotations.media.Schema(accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY,
                description = "UTC last modification time") java.time.Instant updatedAt
) {
    public UserSummaryResponse(User user) {
        this(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getCourse(),
                user.getDepartment(),
                user.getBio(),
                user.getProfilePicture(),
                user.getCreatedAt() == null ? null : user.getCreatedAt().toInstant(java.time.ZoneOffset.UTC),
                user.getUpdatedAt() == null ? null : user.getUpdatedAt().toInstant(java.time.ZoneOffset.UTC)
        );
    }
}
