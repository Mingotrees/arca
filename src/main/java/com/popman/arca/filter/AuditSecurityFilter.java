package com.popman.arca.filter;

import com.popman.arca.service.audit.*;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Observes only external outcomes; committed successes are recorded by mutation services. */
public class AuditSecurityFilter extends OncePerRequestFilter {
    private final SecurityAuditWriter writer;
    private final MeterRegistry meters;
    public AuditSecurityFilter(SecurityAuditWriter writer, MeterRegistry meters) { this.writer = writer; this.meters = meters; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !AuditRequestMatchers.API.matches(request) || "OPTIONS".equals(request.getMethod());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlation = AuditContext.correlation();
        boolean post = "POST".equals(request.getMethod());
        AuditAction securityAction = post && AuditRequestMatchers.LOGIN.matches(request) ? AuditAction.LOGIN
                : post && AuditRequestMatchers.RESET.matches(request) ? AuditAction.PASSWORD_RESET
                : post && AuditRequestMatchers.RECOVERY.matches(request) ? AuditAction.PASSWORD_RECOVERY : null;
        boolean failed = false;
        try { chain.doFilter(request, response); }
        catch (IOException | ServletException | RuntimeException e) { failed = true; throw e; }
        finally {
            int status = failed ? 500 : response.getStatus();
            boolean deniedRoute = AuditRequestMatchers.ADMIN.matches(request) || !"GET".equals(request.getMethod());
            AuditAction action = securityAction != null && (status >= 400 || securityAction == AuditAction.PASSWORD_RECOVERY)
                    ? securityAction : (status == 401 || status == 403) && deniedRoute ? AuditAction.ACCESS_DENIED : null;
            if (action != null) {
                String outcome = status >= 500 ? "ERROR" : status >= 400 ? "DENIED" : "ACCEPTED";
                try { writer.record(action, outcome, securityAction == null ? AuditContext.actorId() : null, correlation); }
                catch (RuntimeException e) {
                    meters.counter("arca.audit.security.write.failures").increment();
                    LoggerFactory.getLogger(AuditSecurityFilter.class).warn("Security audit persistence failed");
                }
            }
        }
    }
}
