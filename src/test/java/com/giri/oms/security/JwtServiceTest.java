package com.giri.oms.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test — JwtService only depends on JwtProperties (a plain record),
 * so it's constructed directly with no Spring context needed.
 */
class JwtServiceTest {

    // Same fixed dev secret as application.properties' default — 50 decoded
    // bytes, comfortably over HS256's 32-byte minimum key length.
    private static final String SECRET = "ZmFrZS1kZXYtb25seS1zZWNyZXQtZG8tbm90LXVzZS1pbi1wcm9kLTEyMzQ1Njc4OTA=";

    private JwtService jwtService;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(SECRET, 60_000L));
        userDetails = User.withUsername("jane.doe")
                .password("irrelevant-for-this-test")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_STAFF")))
                .build();
    }

    @Test
    void generateToken_producesTokenThatExtractsBackToTheSameUsername() {
        String token = jwtService.generateToken(userDetails);

        assertThat(jwtService.extractUsername(token)).isEqualTo("jane.doe");
    }

    @Test
    void generateToken_embedsAuthoritiesAsClaim() {
        String token = jwtService.generateToken(userDetails);

        assertThat(jwtService.extractAuthorities(token)).containsExactly("ROLE_STAFF");
    }

    @Test
    void isTokenValid_true_whenSubjectMatchesAndNotExpired() {
        String token = jwtService.generateToken(userDetails);

        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void isTokenValid_false_whenSubjectDoesNotMatch() {
        String token = jwtService.generateToken(userDetails);
        UserDetails someoneElse = User.withUsername("someone.else")
                .password("irrelevant-for-this-test")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_STAFF")))
                .build();

        assertThat(jwtService.isTokenValid(token, someoneElse)).isFalse();
    }

    @Test
    void isTokenValid_false_whenTokenIsExpired() throws InterruptedException {
        JwtService shortLivedService = new JwtService(new JwtProperties(SECRET, 1L)); // 1ms expiry
        String token = shortLivedService.generateToken(userDetails);

        Thread.sleep(20);

        assertThat(shortLivedService.isTokenValid(token, userDetails)).isFalse();
    }

    @Test
    void isTokenValid_false_whenTokenIsGarbage() {
        assertThat(jwtService.isTokenValid("not-a-real-token", userDetails)).isFalse();
    }

    @Test
    void isTokenValid_false_whenSignedWithADifferentSecret() {
        String differentSecret = "YW5vdGhlci1jb21wbGV0ZWx5LWRpZmZlcmVudC1zZWNyZXQtdmFsdWUtMTIzNDU2Nzg5MA==";
        JwtService otherService = new JwtService(new JwtProperties(differentSecret, 60_000L));
        String token = otherService.generateToken(userDetails);

        assertThat(jwtService.isTokenValid(token, userDetails)).isFalse();
    }
}
