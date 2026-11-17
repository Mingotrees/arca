package com.popman.arca.filter;

import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/** Uses MVC-compatible decoded path segments, including context-path handling. */
final class AuditRequestMatchers {
    private AuditRequestMatchers() {}
    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();
    static final PathPatternRequestMatcher API = PATHS.matcher("/api/v1/**");
    static final PathPatternRequestMatcher ADMIN = PATHS.matcher("/api/v1/admin/**");
    static final PathPatternRequestMatcher LOGIN = PATHS.matcher("/api/v1/auth/login");
    static final PathPatternRequestMatcher RESET = PATHS.matcher("/api/v1/auth/password-reset");
    static final PathPatternRequestMatcher RECOVERY = PATHS.matcher("/api/v1/auth/password-recovery");
}
