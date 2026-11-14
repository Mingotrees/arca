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

/** Runs before security so denied DELETE requests are observable too. */
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
        return !"DELETE".equals(request.getMethod()) || !DELETE_PATH.matcher(path).matches();
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
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            failed = true;
            throw e;
        } finally {
            int status = failed ? 500 : response.getStatus();
            String outcome = status < 400 ? "success" : status < 500 ? "rejected" : "error";
            long nanos = sample.stop(Timer.builder("arca.post.deletion")
                    .description("Post deletion request outcomes and latency, including authorization failures")
                    .tags("status", Integer.toString(status), "outcome", outcome).register(meters));
            // Never log raw paths, request bodies, headers, or credentials.
            log.info("post_delete outcome={} status={} correlation_id={} duration_ms={}",
                    outcome, status, correlation, nanos / 1_000_000.0);
            if (previous == null) MDC.remove("correlation_id");
            else MDC.put("correlation_id", previous);
        }
    }
}
