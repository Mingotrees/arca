package com.popman.arca.dto.v1.user;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CoverPictureResponse(@JsonProperty("user_id") Long userId,
                                   @JsonProperty("cover_picture") String coverPicture,
                                   String message) {}
