package com.giri.oms.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * privateKey/publicKey are single-line Base64 of the full PEM text (PKCS8 private,
 * X.509 public) — see JwtService for how they're decoded back into PEM and parsed.
 * keyId is the "kid" stamped on every issued token and published alongside the
 * public key at /.well-known/jwks.json (see JwksController), so a verifier — this
 * app today, any number of independent services later — can match a token back to
 * the exact public key that validates it, which matters once key rotation means
 * more than one public key is live at once.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String privateKey, String publicKey, String keyId, long expirationMs) {
}
