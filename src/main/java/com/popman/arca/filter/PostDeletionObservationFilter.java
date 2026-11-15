package com.popman.arca.filter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/** Runs before security so denied post and password-recovery requests are observable too. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PostDeletionObservationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(PostDeletionObservationFilter.class);
    private static final Pattern DELETE_PATH = Pattern.compile("/api/v1/posts/[^/]+");
    private static final Pattern SAFE_CORRELATION = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private final MeterRegistry meters;

    public PostDeletionObservationFilter(MeterRegistry meters) {
        this.meters = meters;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean deletion = "DELETE".equals(request.getMethod()) && DELETE_PATH.matcher(path).matches();
        boolean search = "GET".equals(request.getMethod()) && "/api/v1/posts/search".equals(path);
        boolean recovery = "POST".equals(request.getMethod()) &&
                ("/api/v1/auth/password-recovery".equals(path) || "/api/v1/auth/password-reset".equals(path));
        return !deletion && !search && !recovery;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String submitted = request.getHeader("X-Request-ID");
        String correlation = submitted != null && SAFE_CORRELATION.matcher(submitted).matches()
                ? submitted : UUID.randomUUID().toString();
        String previous = MDC.get("correlation_id");
        MDC.put("correlation_id", correlation);
        response.setHeader("X-Request-ID", correlation);
        Timer.Sample sample = Timer.start(meters);
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String event;
        String metric;
        if (path.equals("/api/v1/auth/password-recovery")) { event = "password_recovery"; metric = "arca.auth.password.recovery"; }
        else if (path.equals("/api/v1/auth/password-reset")) { event = "password_reset"; metric = "arca.auth.password.reset"; }
        else if ("GET".equals(request.getMethod())) { event = "post_search"; metric = "arca.post.search"; }
        else { event = "post_delete"; metric = "arca.post.deletion"; }
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            failed = true;
            throw e;
        } finally {
            int status = failed ? 500 : response.getStatus();
            String outcome = status < 400 ? "success" : status < 500 ? "rejected" : "error";
            long nanos = sample.stop(Timer.builder(metric)
                    .description("Request outcomes and latency, including authorization failures")
                    .tags("status", Integer.toString(status), "outcome", outcome).register(meters));
            // Never log raw paths, request bodies, headers, or credentials.
            log.info("{} outcome={} status={} correlation_id={} duration_ms={}",
                    event, outcome, status, correlation, nanos / 1_000_000.0);
            if (previous == null) MDC.remove("correlation_id");
            else MDC.put("correlation_id", previous);
        }
    }
}
