package com.giri.oms.common.ratelimit;

import com.giri.oms.common.exception.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Brute-force protection for /api/auth/login: caps attempts per client IP using a
 * Redis-backed token bucket (see RateLimitConfig), so the limit holds even when
 * requests land on different app instances behind a load balancer. Deliberately NOT a
 * @Component — registered explicitly and scoped to the login path only (see
 * RateLimitFilterRegistration), the same reasoning as JwtAuthenticationFilter.
 */
@Slf4j
@RequiredArgsConstructor
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final ProxyManager<String> proxyManager;
    private final RateLimitProperties properties;
    private final JsonMapper objectMapper;

    private static final String BUCKET_KEY_PREFIX = "ratelimit:login:";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String clientIp = clientIp(request);
        Bucket bucket = proxyManager.builder().build(BUCKET_KEY_PREFIX + clientIp, this::bucketConfiguration);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000);
        log.warn("Login rate limit exceeded for client IP: {}", clientIp);
        writeTooManyRequests(response, request.getRequestURI(), retryAfterSeconds);
    }

    private BucketConfiguration bucketConfiguration() {
        Refill refill = Refill.intervally(properties.refillTokens(), Duration.ofSeconds(properties.refillDurationSeconds()));
        Bandwidth limit = Bandwidth.classic(properties.capacity(), refill);
        return BucketConfiguration.builder().addLimit(limit).build();
    }

    private void writeTooManyRequests(HttpServletResponse response, String path, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                com.giri.oms.common.exception.ErrorCode.RATE_LIMIT_EXCEEDED.code(),
                com.giri.oms.common.exception.ErrorCode.RATE_LIMIT_EXCEEDED.formatMessage(retryAfterSeconds),
                path
        );
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }

    // Behind a load balancer/reverse proxy, the direct socket address is the proxy
    // itself — prefer X-Forwarded-For's first hop (the original client) when present.
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}