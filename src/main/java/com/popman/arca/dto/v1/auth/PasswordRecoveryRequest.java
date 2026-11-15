package com.popman.arca.dto.v1.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;

public record PasswordRecoveryRequest(@NotBlank @Email @Size(max = 254) @Pattern(regexp = PasswordRecoveryRequest.EMAIL_PATTERN) String email) {
    public static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    public PasswordRecoveryRequest { email = email == null ? null : email.strip().toLowerCase(Locale.ROOT); }
    @Override public String toString() { return "PasswordRecoveryRequest[redacted]"; }
}
