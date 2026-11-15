package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.auth.AuthResponse;
import com.popman.arca.dto.v1.auth.LoginRequest;
import com.popman.arca.dto.v1.auth.RegisterRequest;
import com.popman.arca.dto.v1.auth.RegisterResponse;
import com.popman.arca.dto.v1.common.MessageResponse;
import com.popman.arca.dto.v1.refreshtoken.RefreshTokenRequest;
import com.popman.arca.entity.RefreshToken;
import com.popman.arca.entity.User;
import com.popman.arca.service.BannedEmailService;
import com.popman.arca.service.RefreshTokenService;
import com.popman.arca.service.UserService;
import com.popman.arca.service.AuthSessionService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final BannedEmailService bannedEmailService;
    private final AuthSessionService sessions;

    public AuthController(UserService userService,
                          RefreshTokenService refreshTokenService,
                          BannedEmailService bannedEmailService, AuthSessionService sessions) {
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.bannedEmailService = bannedEmailService;
        this.sessions = sessions;
    }

    @PostMapping("/register")
    @ApiResponse(responseCode = "201", description = "Registered",
            content = @Content(schema = @Schema(implementation = RegisterResponse.class)))
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            if (bannedEmailService.isBannedV1(request.getEmail())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Registration blocked: email is banned"));
            }
            User user = request.toUser();
            String result = userService.createUserV1(user);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    new RegisterResponse(result, request.getEmail())
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Registration failed: " + e.getMessage()));
        }
    }

    @PostMapping("/login")
    @ApiResponse(responseCode = "200", description = "Authenticated",
            content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            return ResponseEntity.ok(sessions.login(loginRequest.getEmail(), loginRequest.getPassword()));
        } catch (AuthSessionService.BannedAccountException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(e.getMessage()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid email or password"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Login failed"));
        }
    }

    @PostMapping("/refresh")
    @ApiResponse(responseCode = "200", description = "Token refreshed",
            content = @Content(schema = @Schema(implementation = AuthResponse.class)))
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest request) {
        try {
            return ResponseEntity.ok(sessions.refresh(request.getRefreshToken()));
        } catch (AuthSessionService.BannedAccountException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(e.getMessage()));
        } catch (RuntimeException e) {
            logger.warn("auth_refresh outcome=rejected");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(e instanceof AuthSessionService.InvalidRefreshTokenException ? e.getMessage() : "Invalid refresh token"));
        }
    }

    @PostMapping("/logout")
    @ApiResponse(responseCode = "200", description = "Logged out",
            content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    public ResponseEntity<?> logout(@RequestBody RefreshTokenRequest request) {
        try {
            refreshTokenService.findByToken(request.getRefreshToken())
                    .ifPresent(token -> {
                        refreshTokenService.deleteByUser(token.getUser());
                        logger.info("User logged out: {}", token.getUser().getEmail());
                    });

            return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
        } catch (Exception e) {
            logger.error("Error during logout: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Logout failed: " + e.getMessage()));
        }
    }

    private static class ErrorResponse {
        private String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() { return error; }
    }

}
