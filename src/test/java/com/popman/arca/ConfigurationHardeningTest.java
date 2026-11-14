package com.popman.arca;

import com.popman.arca.config.SecurityConfig;
import com.popman.arca.service.JWTService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Date;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationHardeningTest {
    private final ApplicationContextRunner jwtContext = new ApplicationContextRunner()
            .withUserConfiguration(JWTService.class)
            .withPropertyValues("jwt.access-token-expiration=60000", "jwt.refresh-token-expiration=3600000");

    @ParameterizedTest
    @ValueSource(strings = {"", "not-base64!", "c2hvcnQ=", "${SECRET_KEY}"})
    void refusesUnsafeJwtSecretAtStartup(String secret) {
        jwtContext.withPropertyValues("jwt.secret=" + secret).run(context -> {
            assertNotNull(context.getStartupFailure(), "Unsafe signing key must stop startup");
        });
    }

    @Test
    void acceptsPreviouslyIssuedJwtWithoutChangingClaimsOrKey() {
        byte[] bytes = "old-deployment-key-with-at-least-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String oldToken = Jwts.builder().subject("owner@example.test").expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(bytes)).compact();
        jwtContext.withPropertyValues("jwt.secret=" + Base64.getEncoder().encodeToString(bytes)).run(context -> {
            assertNull(context.getStartupFailure());
            JWTService jwt = context.getBean(JWTService.class);
            assertEquals("owner@example.test", jwt.extractEmail(oldToken));
            assertEquals("owner@example.test", jwt.extractEmail(jwt.generateToken("owner@example.test")));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "*", "https://*.example.test", "null", "https://example.test/path",
            "https://user@example.test", "http://example.test", "https://example.test?query=1", "https://example.test#fragment"})
    void rejectsUnsafeProductionCorsOrigins(String origin) {
        SecurityConfig config = cors(origin, "prod");
        assertThrows(IllegalArgumentException.class, config::corsConfigurationSource);
    }

    @Test
    void acceptsExplicitDevelopmentAndProductionOrigins() {
        assertDoesNotThrow(() -> cors("http://localhost:5173", "dev").corsConfigurationSource());
        assertDoesNotThrow(() -> cors("http://192.168.1.10:5173", "dev").corsConfigurationSource());
        assertDoesNotThrow(() -> cors("https://arca.example.test", "prod").corsConfigurationSource());
    }

    private SecurityConfig cors(String origin, String profile) {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "allowedOrigins", List.of(origin));
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        // Environment is injected when configuration is managed by Spring.
        ReflectionTestUtils.setField(config, "environment", environment);
        return config;
    }
}
