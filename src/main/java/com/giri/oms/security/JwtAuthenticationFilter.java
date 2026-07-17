package com.giri.oms.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request. Looks for a "Bearer <token>" Authorization header,
 * validates the token, and — if valid — populates the SecurityContext so the
 * rest of the request is treated as authenticated. No token, or an invalid
 * one, and the request simply proceeds unauthenticated; it's the downstream
 * authorization rules (see SecurityConfig) that decide whether that's allowed.
 *
 * Deliberately NOT a @Component: this class implements Filter (via
 * OncePerRequestFilter), and any Filter bean discovered by component-scanning
 * gets swept into every @WebMvcTest slice regardless of which controller is
 * under test, dragging in JwtService/UserDetailsService (both @Service, not
 * part of a web-layer slice) and failing the context for unrelated tests. It
 * would also risk Spring Boot auto-registering it a second time as a
 * container-level filter, on top of the explicit addFilterBefore(...) wiring
 * below. Instead, SecurityConfig constructs it directly as a plain object.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        if (tokenBlacklistService.isBlacklisted(token)) {
            log.debug("Rejected blacklisted JWT (logged out)");
            filterChain.doFilter(request, response);
            return;
        }

        String username;
        try {
            username = jwtService.extractUsername(token);
        } catch (Exception ex) {
            log.debug("Could not extract username from JWT: {}", ex.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        SecurityContext context = SecurityContextHolder.getContext();
        if (username != null && context.getAuthentication() == null) {
            UserDetails userDetails;
            try {
                userDetails = userDetailsService.loadUserByUsername(username);
            } catch (org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
                // Token refers to a user that no longer exists (e.g. deleted after
                // the token was issued) — treat the request as unauthenticated
                // rather than failing it outright.
                log.debug("JWT subject '{}' no longer exists", username);
                filterChain.doFilter(request, response);
                return;
            }

            if (jwtService.isTokenValid(token, userDetails)) {
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                context.setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}
