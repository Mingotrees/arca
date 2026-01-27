package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.auth.AuthResponse;
import com.popman.arca.dto.v1.auth.LoginRequest;
import com.popman.arca.dto.v1.refreshtoken.RefreshTokenRequest;
import com.popman.arca.entity.RefreshToken;
import com.popman.arca.entity.User;
import com.popman.arca.service.JWTService;
import com.popman.arca.service.BannedEmailService;
import com.popman.arca.service.RefreshTokenService;
import com.popman.arca.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final JWTService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final BannedEmailService bannedEmailService;

    public AuthController(UserService userService,
                          JWTService jwtService,
                          RefreshTokenService refreshTokenService,
                          AuthenticationManager authenticationManager,
                          BannedEmailService bannedEmailService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.authenticationManager = authenticationManager;
        this.bannedEmailService = bannedEmailService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        try {
            if (bannedEmailService.isBannedV1(user.getEmail())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Registration blocked: email is banned"));
            }
            String result = userService.createUserV1(user);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    new RegisterResponse(result, user.getEmail())
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
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            if (bannedEmailService.isBannedV1(loginRequest.getEmail())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("Login blocked: email is banned"));
            }
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            if (authentication.isAuthenticated()) {
                // Generate access token
                String accessToken = jwtService.generateToken(loginRequest.getEmail());

                // Generate refresh token
                RefreshToken refreshToken = refreshTokenService.createRefreshToken(loginRequest.getEmail());


                // Return both tokens
                AuthResponse response = new AuthResponse(
                        accessToken,
                        refreshToken.getToken(),
                        loginRequest.getEmail()
                );

                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("Authentication failed"));
            }
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Invalid email or password"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Login failed: " + e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest request) {
        try {
            RefreshToken refreshToken = refreshTokenService.findByToken(request.getRefreshToken())
                    .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

            refreshTokenService.verifyExpiration(refreshToken);

            User user = refreshToken.getUser();
            if (bannedEmailService.isBannedV1(user.getEmail())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("Refresh blocked: email is banned"));
            }
            String newAccessToken = jwtService.generateToken(user.getEmail());

            logger.info("Token refreshed for user: {}", user.getEmail());

            AuthResponse response = new AuthResponse(
                    newAccessToken,
                    request.getRefreshToken(),
                    user.getEmail()
            );

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            logger.error("Error refreshing token: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/logout")
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

    private static class RegisterResponse {
        private String message;
        private String email;

        public RegisterResponse(String message, String email) {
            this.message = message;
            this.email = email;
        }

        public String getMessage() { return message; }
        public String getEmail() { return email; }
    }

    private static class ErrorResponse {
        private String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() { return error; }
    }

    private static class MessageResponse {
        private String message;

        public MessageResponse(String message) {
            this.message = message;
        }

        public String getMessage() { return message; }
    }
}