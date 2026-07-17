package com.giri.oms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates and validates HS256-signed JWTs. Stateless by design — everything
 * a request needs (username + authorities) travels inside the token itself, so
 * there's no server-side session to look up on each call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String AUTHORITIES_CLAIM = "authorities";

    private final JwtProperties jwtProperties;

    private SecretKey signingKey() {
        // The configured secret is Base64 text, not raw bytes — decode it first.
        // Keys.hmacShaKeyFor also enforces the HS256 minimum key length (32 bytes)
        // at startup, so a too-short secret fails fast instead of producing a
        // token that quietly can't be trusted.
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret()));
    }

    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.expirationMs());

        List<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return Jwts.builder()
                .id(UUID.randomUUID().toString()) // jti — guarantees uniqueness even if iat/exp
                // (second-precision NumericDate) collide with
                // another token for the same user issued in the
                // same second; without this, HS256's deterministic
                // signing would make those two tokens byte-for-byte
                // identical, so blacklisting one (e.g. via logout)
                // would silently blacklist the other too.
                .subject(userDetails.getUsername())
                .claim(AUTHORITIES_CLAIM, authorities)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractAuthorities(String token) {
        return (List<String>) parseClaims(token).get(AUTHORITIES_CLAIM, List.class);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = parseClaims(token);
            return claims.getSubject().equals(userDetails.getUsername()) && !isExpired(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected invalid JWT: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * How much longer this token has left before it expires on its own. Used to size
     * the TTL of a blacklist entry on logout — the entry only needs to outlive the
     * token itself, never longer.
     */
    public Duration getRemainingValidity(String token) {
        Date expiry = parseClaims(token).getExpiration();
        long millis = expiry.getTime() - System.currentTimeMillis();
        return Duration.ofMillis(Math.max(millis, 0));
    }

    private boolean isExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            // Still return the claims of an expired token — callers that just want
            // the username (e.g. for logging) can have it; isTokenValid separately
            // checks expiry and will correctly reject it.
            return ex.getClaims();
        }
    }
}