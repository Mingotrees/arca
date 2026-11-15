package com.popman.arca.dto.v1.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.popman.arca.entity.User;

import java.util.List;

public class UserResponse {
    private final Long id;

    @JsonProperty("first_name")
    private final String firstName;

    @JsonProperty("last_name")
    private final String lastName;

    private final String email;
    private final String course;
    private final String department;
    private final String bio;

    @JsonProperty("profile_picture")
    private final String profilePicture;

    @JsonProperty("cover_picture")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    @io.swagger.v3.oas.annotations.media.Schema(accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY,
            description = "Optional relative cover storage key; retrieve bytes from GET /api/v1/user/{userId}/cover-picture")
    private final String coverPicture;

    public String getCoverPicture() { return coverPicture; }

    private final List<String> roles;

    @JsonProperty("created_at")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    @io.swagger.v3.oas.annotations.media.Schema(accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY,
            description = "UTC creation time; deployment-time approximation for legacy users")
    private final java.time.Instant createdAt;

    @JsonProperty("updated_at")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    @io.swagger.v3.oas.annotations.media.Schema(accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, description = "UTC last modification time")
    private final java.time.Instant updatedAt;

    public UserResponse(User user) {
        this.id = user.getId();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.email = user.getEmail();
        this.course = user.getCourse();
        this.department = user.getDepartment();
        this.bio = user.getBio();
        this.profilePicture = user.getProfilePicture();
        this.coverPicture = user.getCoverPicture();
        this.createdAt = user.getCreatedAt() == null ? null : user.getCreatedAt().toInstant(java.time.ZoneOffset.UTC);
        this.updatedAt = user.getUpdatedAt() == null ? null : user.getUpdatedAt().toInstant(java.time.ZoneOffset.UTC);
        this.roles = user.getRoles() == null
                ? List.of()
                : user.getRoles().stream().sorted().toList();
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getCourse() {
        return course;
    }

    public String getDepartment() {
        return department;
    }

    public String getBio() {
        return bio;
    }

    public String getProfilePicture() {
        return profilePicture;
    }

    public List<String> getRoles() {
        return roles;
    }
    public java.time.Instant getCreatedAt() { return createdAt; }
    public java.time.Instant getUpdatedAt() { return updatedAt; }
}
