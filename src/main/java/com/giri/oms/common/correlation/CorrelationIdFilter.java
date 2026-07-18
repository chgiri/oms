package com.giri.oms.common.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

import static com.giri.oms.common.correlation.CorrelationIdConstants.HEADER_NAME;
import static com.giri.oms.common.correlation.CorrelationIdConstants.MDC_KEY;

/**
 * Assigns every request a correlation ID — reused from the caller's
 * X-Correlation-Id header if present (so an upstream gateway or another
 * service can thread its own ID through), otherwise generated fresh — and
 * puts it in MDC so every log line emitted while handling this request
 * carries it automatically (see logging.pattern.console in
 * application.properties). Echoed back on the response so the caller can
 * match their own logs against ours, or hand it back to support/QA.
 *
 * Deliberately NOT a @Component and registered with the earliest possible
 * order (see CorrelationIdFilterRegistration) — every other filter,
 * including Spring Security's chain and the login rate limiter, should
 * already have a correlation ID available when *they* log something. Same
 * reasoning as JwtAuthenticationFilter and LoginRateLimitFilter for not
 * being a @Component.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // MDC is thread-local and this thread goes back to a pool afterwards —
            // always clear it, success or exception, or the next request served
            // by this thread silently inherits a stale correlation ID.
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String incoming = request.getHeader(HEADER_NAME);
        if (incoming != null && !incoming.isBlank()) {
            return incoming.trim();
        }
        return UUID.randomUUID().toString();
    }
}
