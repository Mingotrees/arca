package com.popman.arca.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class AuditCorrelationFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !AuditRequestMatchers.API.matches(request);
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String submitted = request.getHeader("X-Request-ID");
        String correlation = submitted != null && submitted.matches("[A-Za-z0-9._-]{1,64}") ? submitted : UUID.randomUUID().toString();
        String previous = MDC.get("correlation_id");
        MDC.put("correlation_id", correlation); response.setHeader("X-Request-ID", correlation);
        try { chain.doFilter(request, response); }
        finally { if (previous == null) MDC.remove("correlation_id"); else MDC.put("correlation_id", previous); }
    }
}
