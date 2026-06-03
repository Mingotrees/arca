package com.popman.arca.filter;

import com.popman.arca.service.BannedEmailService;
import com.popman.arca.service.JWTService;
import com.popman.arca.service.MyUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtFilter.class);

    private final JWTService jwtService;
    private final BannedEmailService bannedEmailService;
    private final MyUserDetailsService userDetailsService;

    public JwtFilter(JWTService jwtService, BannedEmailService bannedEmailService, MyUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.bannedEmailService = bannedEmailService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String email;
        try {
            email = jwtService.extractEmail(token);
        } catch (Exception ex) {
            logger.warn("Invalid token format: {}", ex.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        if (email == null || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (bannedEmailService.isBannedV1(email)) {
                String origin = request.getHeader("Origin");
                if (origin != null) {
                    response.setHeader("Access-Control-Allow-Origin", origin);
                    response.setHeader("Vary", "Origin");
                    response.setHeader("Access-Control-Allow-Credentials", "true");
                }
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"error\":\"Access blocked: email is banned\"}");
                return;
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            if (!jwtService.validateToken(token, userDetails)) {
                logger.warn("Invalid JWT for user {}", email);
                filterChain.doFilter(request, response);
                return;
            }

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        } catch (Exception ex) {
            logger.warn("JWT authentication skipped for {}: {}", email, ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
