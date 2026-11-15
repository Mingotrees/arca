package com.popman.arca.dto.v1.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank @Size(min = 43, max = 43) @Pattern(regexp = "[A-Za-z0-9_-]{43}") @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String token,
        @JsonProperty("new_password") @NotBlank @Size(min = 8, max = 72) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY) String newPassword) {
    @Override public String toString() { return "PasswordResetRequest[redacted]"; }
}
