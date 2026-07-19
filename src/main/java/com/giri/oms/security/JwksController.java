package com.giri.oms.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes this service's RSA public key in JWK format at the conventional
 * /.well-known/jwks.json path — the standard way an independent verifier (a future
 * microservice, an API gateway, a resource-server library) discovers how to check
 * this app's tokens without ever holding the private key that signs them.
 * <p>
 * Deliberately a plain {@code @Controller} + {@code @ResponseBody} rather than
 * {@code @RestController}: {@link com.giri.oms.common.config.WebConfig} prefixes
 * every {@code @RestController} with {@code /api/v1}, but a JWKS document is
 * expected at a fixed, unversioned, well-known URL by convention (RFC 8615) — every
 * client that ever fetches this (this app today, others later) expects it at
 * exactly this path regardless of API version. {@code @Controller} +
 * {@code @ResponseBody} still serializes the return value as JSON the same way
 * {@code @RestController} does, but {@code WebConfig}'s
 * {@code isAnnotationPresent(RestController.class)} predicate correctly does not
 * match it, so it's never prefixed.
 */
@Controller
@RequiredArgsConstructor
public class JwksController {

    private final JwtService jwtService;

    @GetMapping("/.well-known/jwks.json")
    @ResponseBody
    public Map<String, Object> getJwks() {
        RSAPublicKey publicKey = jwtService.getPublicKey();

        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", jwtService.getKeyId());
        jwk.put("n", base64Url(publicKey.getModulus()));
        jwk.put("e", base64Url(publicKey.getPublicExponent()));

        Map<String, Object> jwks = new LinkedHashMap<>();
        jwks.put("keys", List.of(jwk));
        return jwks;
    }

    /**
     * JWK requires unsigned, unpadded Base64URL. BigInteger.toByteArray() can
     * prepend a leading 0x00 sign byte for a value whose high bit is set even
     * though it's mathematically positive — that byte must be stripped or every
     * client re-deriving the key gets the wrong modulus/exponent.
     */
    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
