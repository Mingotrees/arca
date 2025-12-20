package com.popman.arca.filter;


import com.popman.arca.service.JWTService;
import com.popman.arca.service.BannedEmailService;
import com.popman.arca.service.MyUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JWTService jwtService;

    @Autowired
    private ApplicationContext context;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        String token = null;
        String email = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            email = jwtService.extractEmail(token);
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            BannedEmailService bannedEmailService = context.getBean(BannedEmailService.class);
            if (bannedEmailService.isBanned(email)) {
                // Ensure CORS headers are present on short-circuit responses
                String origin = request.getHeader("Origin");
                if (origin != null) {
                    response.setHeader("Access-Control-Allow-Origin", origin);
                    response.setHeader("Vary", "Origin");
                    response.setHeader("Access-Control-Allow-Credentials", "true");
                }
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Access blocked: email is banned\"}");
                return;
            }
            UserDetails userDetails = context.getBean(MyUserDetailsService.class).loadUserByUsername(email);



            if (jwtService.validateToken(token, userDetails)) {

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }else{
                System.out.println("Token is INVALID");
            }
        }

        filterChain.doFilter(request, response);
    }
}
