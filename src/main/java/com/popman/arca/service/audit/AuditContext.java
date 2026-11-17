package com.popman.arca.service.audit;

import com.popman.arca.entity.UserPrincipal;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.UUID;

public final class AuditContext {
    private AuditContext() {}
    public static Long actorId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof UserPrincipal principal
                ? principal.getId() : null;
    }
    public static String correlation() {
        String value = MDC.get("correlation_id");
        return value != null && value.matches("[A-Za-z0-9._-]{1,64}") ? value : UUID.randomUUID().toString();
    }
}
