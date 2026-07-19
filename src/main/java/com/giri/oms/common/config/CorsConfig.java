package com.giri.oms.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Browser-facing CORS policy for the Angular frontend. Spring Security's own CSRF/session
 * handling is irrelevant here (the API is stateless JWT, see SecurityConfig), but the
 * browser still enforces same-origin unless the server explicitly allows the frontend's
 * origin — without this, every request from ng serve/production Angular build fails at
 * the browser before it even reaches a controller.
 * <p>
 * Allowed origins are externalized (app.cors.allowed-origins) rather than hardcoded, so
 * local dev (http://localhost:4200), a docker-composed frontend, and a real prod domain
 * can each set their own value without a code change. Credentials (cookies) are not used
 * by this API — the token travels in an Authorization header — so allow-credentials stays
 * false and allowed-origins can safely be a concrete list (never "*") for header-based auth.
 */
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Authorization: Bearer <token>, plus the correlation id the frontend sends per
        // request (see CorrelationIdFilter) and standard content headers.
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        // Lets the frontend read the correlation id back off the response too, useful
        // when logging/reporting a failed request from the browser side.
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
