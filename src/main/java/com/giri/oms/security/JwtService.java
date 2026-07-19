package com.giri.oms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates and validates RS256-signed JWTs. Stateless by design — everything a
 * request needs (username + authorities) travels inside the token itself, so
 * there's no server-side session to look up on each call.
 * <p>
 * RS256 (asymmetric) rather than HS256 (shared secret) is deliberate: this app
 * signs with the private key, but verifying a token only ever needs the public
 * key, published at /.well-known/jwks.json (see JwksController). Today that's a
 * distinction without a difference — one app, one key. It matters the moment a
 * second service needs to verify tokens issued here: with HS256 that service would
 * need the actual signing secret (i.e. the power to also mint tokens), whereas
 * with RS256 it only ever fetches the public key and can verify, never issue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String AUTHORITIES_CLAIM = "authorities";

    private final JwtProperties jwtProperties;

    // Parsed once at startup rather than per-request — unlike the old HMAC key
    // (a cheap Base64 decode), parsing a PEM into an RSA key involves ASN.1
    // decoding that's wasteful to repeat on every single request.
    private PrivateKey privateKey;
    private RSAPublicKey publicKey;

    @PostConstruct
    void init() {
        this.privateKey = parsePrivateKey(jwtProperties.privateKey());
        this.publicKey = parsePublicKey(jwtProperties.publicKey());
    }

    /**
     * The public half of the signing key, exposed so JwksController can publish it.
     * Never expose {@link #privateKey} the same way — that one must never leave
     * this service.
     */
    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public String getKeyId() {
        return jwtProperties.keyId();
    }

    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.expirationMs());

        List<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return Jwts.builder()
                .header().keyId(jwtProperties.keyId()).and()
                .id(UUID.randomUUID().toString()) // jti — guarantees uniqueness even if iat/exp
                // (second-precision NumericDate) collide with another token for the
                // same user issued in the same second.
                .subject(userDetails.getUsername())
                .claim(AUTHORITIES_CLAIM, authorities)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(privateKey, Jwts.SIG.RS256)
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
                    .verifyWith(publicKey)
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

    private static PrivateKey parsePrivateKey(String base64OfPem) {
        try {
            String pkcs8Der = stripPemHeaders(decodeBase64ToPem(base64OfPem));
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pkcs8Der));
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception ex) {
            // Fails fast at startup rather than at the first login attempt — a
            // malformed/misconfigured key should never make it into a running app.
            throw new IllegalStateException("Could not parse jwt.private-key — expected Base64 of a PKCS8 PEM", ex);
        }
    }

    private static RSAPublicKey parsePublicKey(String base64OfPem) {
        try {
            String x509Der = stripPemHeaders(decodeBase64ToPem(base64OfPem));
            X509EncodedKeySpec spec = new X509EncodedKeySpec(Base64.getDecoder().decode(x509Der));
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not parse jwt.public-key — expected Base64 of an X.509 PEM", ex);
        }
    }

    private static String decodeBase64ToPem(String base64OfPem) {
        return new String(Base64.getDecoder().decode(base64OfPem));
    }

    private static String stripPemHeaders(String pem) {
        return pem.replaceAll("-----BEGIN (.*)-----", "")
                .replaceAll("-----END (.*)-----", "")
                .replaceAll("\\s", "");
    }
}
