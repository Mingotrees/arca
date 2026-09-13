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

    private final List<String> roles;

    public UserResponse(User user) {
        this.id = user.getId();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.email = user.getEmail();
        this.course = user.getCourse();
        this.department = user.getDepartment();
        this.bio = user.getBio();
        this.profilePicture = user.getProfilePicture();
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
}
