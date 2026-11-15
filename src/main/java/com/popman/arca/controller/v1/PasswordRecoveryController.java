package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.auth.PasswordRecoveryRequest;
import com.popman.arca.dto.v1.auth.PasswordResetRequest;
import com.popman.arca.dto.v1.common.ApiErrorResponse;
import com.popman.arca.dto.v1.common.MessageResponse;
import com.popman.arca.service.recovery.PasswordRecoveryService;
import com.popman.arca.service.recovery.RecoveryClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@ApiResponse(responseCode = "400", description = "Invalid request or invalid/expired reset token",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "429", description = "Recovery email/client-IP budget exhausted",
        headers = @Header(name = "Retry-After", description = "Seconds until retry", schema = @Schema(type = "integer", format = "int64")),
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "503", description = "Password recovery not configured/enabled",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
public class PasswordRecoveryController {
    private final PasswordRecoveryService recovery;
    private final RecoveryClientIpResolver clients;
    public PasswordRecoveryController(PasswordRecoveryService recovery, RecoveryClientIpResolver clients) {
        this.recovery = recovery; this.clients = clients;
    }

    @PostMapping("/password-recovery")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @Operation(summary = "Request password recovery", description = "Anonymous. Always returns the same accepted message for eligible, missing, deleted or banned accounts. "
            + "A configured email provider receives a single-use reset link after commit; provider errors do not disclose eligibility. "
            + "Recovery must be configured/enabled before use. Email is normalized for shared per-email and trusted-client-IP limits.",
            parameters = @Parameter(name = "X-Request-ID", in = ParameterIn.HEADER, description = "Optional safe correlation ID; invalid values are replaced by a UUID", schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "202", description = "Generic recovery acknowledgment; no token/account details",
            headers = @Header(name = "X-Request-ID", description = "Safe request correlation ID", schema = @Schema(type = "string")),
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessageResponse.class)))
    public ResponseEntity<MessageResponse> request(@Valid @RequestBody PasswordRecoveryRequest body, HttpServletRequest request) {
        recovery.request(body.email(), clients.resolve(request));
        return ResponseEntity.accepted().body(new MessageResponse("If the account is eligible, password reset instructions will be sent."));
    }

    @PostMapping("/password-reset")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @Operation(summary = "Complete password reset", description = "Anonymous. Submit the 43-character token from the emailed URL fragment and new_password. "
            + "Password requires 8+ characters, uppercase/lowercase/digit and at most 72 UTF-8 bytes. Token is single-use, expires after configured TTL "
            + "(default 15 minutes), and newer requests supersede earlier unused tokens. Success revokes all refresh sessions and requires fresh login; "
            + "existing access JWTs retain their remaining normal lifetime.",
            parameters = @Parameter(name = "X-Request-ID", in = ParameterIn.HEADER, description = "Optional safe correlation ID; invalid values are replaced by a UUID", schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "200", description = "Password changed; sign in again",
            headers = @Header(name = "X-Request-ID", description = "Safe request correlation ID", schema = @Schema(type = "string")),
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessageResponse.class)))
    public ResponseEntity<MessageResponse> reset(@Valid @RequestBody PasswordResetRequest body, HttpServletRequest request) {
        recovery.reset(body.token(), body.newPassword(), clients.resolve(request));
        return ResponseEntity.ok(new MessageResponse("Password reset successfully. Sign in again."));
    }
}
