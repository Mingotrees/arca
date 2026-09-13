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
        @JsonProperty("profile_picture") String profilePicture
) {
    public UserSummaryResponse(User user) {
        this(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getCourse(),
                user.getDepartment(),
                user.getBio(),
                user.getProfilePicture()
        );
    }
}
