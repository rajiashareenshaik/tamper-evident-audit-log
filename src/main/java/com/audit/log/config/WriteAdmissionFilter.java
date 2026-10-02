package com.audit.log.config;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.Semaphore;

/** Keeps waiting writers from consuming every database connection. */
@Component
public class WriteAdmissionFilter extends OncePerRequestFilter {
    private final Semaphore permits;
    private final io.micrometer.core.instrument.Counter rejected;

    public WriteAdmissionFilter(@Value("${audit.max-concurrent-writes:8}") int maximum,
                                MeterRegistry registry) {
        if (maximum < 1) throw new IllegalArgumentException("Write concurrency must be positive");
        permits = new Semaphore(maximum);
        rejected = registry.counter("audit.writes.rejected");
        registry.gauge("audit.writes.active", permits, value -> maximum - value.availablePermits());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod()) ||
                !(path.equals("/api/v1/audit/events") || path.equals("/api/v1/audit/client-account-access"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!permits.tryAcquire()) {
            rejected.increment();
            response.setStatus(503);
            response.setHeader("Retry-After", "1");
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many writes are in progress. Try again shortly.\"}");
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            permits.release();
        }
    }
}
