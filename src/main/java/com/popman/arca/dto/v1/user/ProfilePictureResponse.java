package com.popman.arca.dto.v1.user;

public class ProfilePictureResponse {
    private Long userId;
    private String profilePicture;
    private String message;

    public ProfilePictureResponse() {}

    public ProfilePictureResponse(Long userId, String profilePicture, String message) {
        this.userId = userId;
        this.profilePicture = profilePicture;
        this.message = message;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getProfilePicture() { return profilePicture; }
    public void setProfilePicture(String profilePicture) { this.profilePicture = profilePicture; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
