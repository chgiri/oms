package com.giri.oms.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * JWTs are stateless by design (see JwtService) — there's no server-side session to
 * delete on logout, so a token normally stays valid until it naturally expires even
 * after the user logs out. This adds the one thing pure statelessness can't do:
 * revocation. On logout, the token's signature hash is written to Redis with a TTL
 * equal to its own remaining lifetime, so the blacklist entry disappears on its own
 * the moment the token would have expired anyway — nothing to clean up.
 *
 * Redis (not an in-memory Set) matters here specifically because the app runs as
 * multiple instances in production: a token blacklisted via instance A must also be
 * rejected by instance B.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "blacklist:jwt:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Blacklists a token for exactly as long as it would otherwise remain valid.
     * A token that's already expired is not written at all — nothing left to revoke.
     */
    public void blacklist(String token, Duration remainingValidity) {
        if (remainingValidity.isZero() || remainingValidity.isNegative()) {
            return;
        }
        redisTemplate.opsForValue().set(key(token), "1", remainingValidity);
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(token)));
    }

    // Store a hash rather than the raw token: keeps Redis keys short and avoids
    // persisting bearer tokens verbatim in a store that other tooling may inspect.
    private String key(String token) {
        return KEY_PREFIX + sha256Hex(token);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is a mandatory JDK algorithm — this can't actually happen.
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
