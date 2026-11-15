package com.popman.arca.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@ConfigurationProperties("app.password-recovery")
public record PasswordRecoveryProperties(
        @DefaultValue("false") boolean enabled, URI resetLinkBaseUrl, String from,
        @DefaultValue("PT15M") Duration tokenTtl, @DefaultValue("PT15M") Duration rateWindow,
        @DefaultValue("3") int emailLimit, @DefaultValue("10") int ipLimit, @DefaultValue("20") int resetIpLimit,
        String rateKeySecret, List<String> trustedProxies) {
    public PasswordRecoveryProperties {
        trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
        if (tokenTtl == null || tokenTtl.compareTo(Duration.ofMinutes(1)) < 0 || tokenTtl.compareTo(Duration.ofHours(1)) > 0
                || rateWindow == null || rateWindow.compareTo(Duration.ofMinutes(1)) < 0 || rateWindow.compareTo(Duration.ofDays(1)) > 0
                || emailLimit < 1 || ipLimit < 1 || resetIpLimit < 1) {
            throw new IllegalArgumentException("Invalid password recovery lifetime or rate limits");
        }
    }

    public void validate(Environment environment) {
        if (!enabled) return;
        if (!"none".equalsIgnoreCase(environment.getProperty("server.forward-headers-strategy", "none"))) {
            throw new IllegalArgumentException("Password recovery requires forward-headers-strategy=none and its explicit trusted-proxy policy");
        }
        if (resetLinkBaseUrl == null || resetLinkBaseUrl.getHost() == null || resetLinkBaseUrl.getUserInfo() != null
                || resetLinkBaseUrl.getQuery() != null || resetLinkBaseUrl.getFragment() != null
                || !("https".equals(resetLinkBaseUrl.getScheme()) || "http".equals(resetLinkBaseUrl.getScheme()))) {
            throw new IllegalArgumentException("Configure an explicit HTTP(S) password recovery frontend URL without query or fragment");
        }
        if (!development(environment) && !"https".equals(resetLinkBaseUrl.getScheme())) {
            throw new IllegalArgumentException("Production password recovery links require HTTPS");
        }
        rateKeyBytes();
        if (from == null || from.isBlank() || from.contains("\r") || from.contains("\n")) {
            throw new IllegalArgumentException("Configure a password recovery sender");
        }
    }

    public byte[] rateKeyBytes() {
        try {
            byte[] bytes = Base64.getDecoder().decode(rateKeySecret == null ? "" : rateKeySecret);
            if (bytes.length < 32) throw new IllegalArgumentException();
            return bytes;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Password recovery rate-key-secret must encode at least 32 secret bytes");
        }
    }

    public static boolean development(Environment environment) {
        return environment.acceptsProfiles(Profiles.of("dev", "test"))
                && !environment.acceptsProfiles(Profiles.of("prod", "production"));
    }

    @Override public String toString() { return "PasswordRecoveryProperties[enabled=" + enabled + ", secrets=redacted]"; }
}
