package com.popman.arca.service;

import com.popman.arca.dto.v1.auth.AuthResponse;
import com.popman.arca.repository.RefreshTokenRepository;
import com.popman.arca.repository.UserRepository;
import org.springframework.security.authentication.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;

/** Session creation and validation share the account lock used by password reset. */
@Service
public class AuthSessionService {
    private final UserRepository users;
    private final RefreshTokenRepository sessions;
    private final RefreshTokenService refresh;
    private final JWTService jwt;
    private final AuthenticationManager authentication;
    private final BannedEmailService banned;
    public AuthSessionService(UserRepository users, RefreshTokenRepository sessions, RefreshTokenService refresh,
                              JWTService jwt, AuthenticationManager authentication, BannedEmailService banned) {
        this.users = users; this.sessions = sessions; this.refresh = refresh; this.jwt = jwt;
        this.authentication = authentication; this.banned = banned;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AuthResponse login(String email, String password) {
        var user = users.findByEmailForUpdate(email).orElse(null);
        if (banned.isBannedV1(email)) throw new BannedAccountException("Login blocked: email is banned");
        var result = authentication.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        if (user == null || !result.isAuthenticated()) throw new BadCredentialsException("Invalid email or password");
        var session = refresh.createRefreshToken(user.getEmail());
        return new AuthResponse(jwt.generateToken(user.getEmail()), session.getToken(), user.getEmail());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = InvalidRefreshTokenException.class)
    public AuthResponse refresh(String raw) {
        var candidate = sessions.findByToken(raw).orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));
        var user = users.findByIdForUpdate(candidate.getUser().getId())
                .orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));
        // A locking current read is essential: a pre-reset MVCC snapshot may still contain the deleted session.
        var session = sessions.findByTokenForUpdate(raw).orElseThrow(() -> new InvalidRefreshTokenException("Invalid refresh token"));
        if (!session.getExpiryDate().isAfter(Instant.now())) {
            sessions.delete(session);
            throw new InvalidRefreshTokenException("Refresh token expired. Please login again.");
        }
        if (banned.isBannedV1(user.getEmail())) throw new BannedAccountException("Refresh blocked: email is banned");
        return new AuthResponse(jwt.generateToken(user.getEmail()), session.getToken(), user.getEmail());
    }

    public static class BannedAccountException extends RuntimeException {
        public BannedAccountException(String message) { super(message); }
    }
    public static class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException(String message) { super(message); }
    }
}
