package com.popman.arca.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@DynamicUpdate
@SQLDelete(sql = "UPDATE users SET is_deleted = true, deleted_at = NOW(), updated_at = GREATEST(UTC_TIMESTAMP(6), DATE_ADD(updated_at, INTERVAL 1 MICROSECOND)) WHERE id = ?")
@SQLRestriction("is_deleted = false")
@Table(name = "users", indexes = {
        @Index(name = "idx_user_id", columnList = "id"),
        @Index(name = "idx_email", columnList = "email"),  // Added - critical for login lookups
        @Index(name = "idx_deleted", columnList = "is_deleted")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String firstName;
    private String lastName;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Column(unique = true, nullable = false)  // Email should be unique and required
    private String email;

    @Column(length = 24)
    private String course;
    private String department;

    @Column(length = 50)
    private String bio;
    private String profilePicture;

    @JsonIgnore
    @Column(name = "is_deleted")
    private boolean isDeleted = false;
    @JsonIgnore
    private LocalDateTime deletedAt;

    @JsonIgnore
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @JsonIgnore
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @JsonIgnore
    @Column(name = "timestamps_approximate", nullable = false, updatable = false)
    private boolean timestampsApproximate;

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public boolean isTimestampsApproximate() { return timestampsApproximate; }

    public void touchUpdatedAt() {
        if (updatedAt == null) return;
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        updatedAt = now.isAfter(updatedAt) ? now : updatedAt.plusNanos(1000);
    }

    @PrePersist
    private void initializeProfileTimestamps() {
        createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        updatedAt = createdAt;
        timestampsApproximate = false;
    }

    @PreUpdate
    private void updateProfileTimestamp() { touchUpdatedAt(); }

    // Store multiple roles per user
    // Uses a separate table: user_roles (user_id, role)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            indexes = @Index(name = "idx_user_roles_user_id", columnList = "user_id")
    )
    @Column(name = "role")
    private Set<String> roles;

    // Constructors
    public User() {
        this.roles = new HashSet<>();
    }

    public User(Long id, String firstName, String lastName, String password,
                String email, String course, String department, String bio,
                String profilePicture) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.password = password;
        this.email = email;
        this.course = course;
        this.department = department;
        this.bio = bio;
        this.profilePicture = profilePicture;
        this.roles = new HashSet<>();
    }

    // Helper methods for managing roles
    public void addRole(String role) {
        if (this.roles.add(role)) touchUpdatedAt();
    }

    public void removeRole(String role) {
        if (this.roles.remove(role)) touchUpdatedAt();
    }

    public boolean hasRole(String role) {
        return this.roles.contains(role);
    }

    // Getters and Setters
    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        if (!java.util.Objects.equals(this.roles, roles)) touchUpdatedAt();
        this.roles = roles;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = course;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getProfilePicture() {
        return profilePicture;
    }

    public void setProfilePicture(String profilePicture) {
        this.profilePicture = profilePicture;
    }

}
