package com.giri.oms.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test — JwtService only depends on JwtProperties (a plain record), so
 * it's constructed directly with no Spring context needed. Because there's no
 * Spring context, {@code @PostConstruct} never fires on its own here — each test
 * calls {@link JwtService#init()} manually right after construction, standing in
 * for what the container does automatically at runtime.
 */
class JwtServiceTest {

    // Same fixed dev key pair as application.properties' default — a throwaway
    // 2048-bit RSA pair, checked in for tests/local dev only.
    private static final String PRIVATE_KEY =
            "LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUV2UUlCQURBTkJna3Foa2lHOXcwQkFRRUZBQVNDQktjd2dnU2pBZ0VBQW9JQkFRRE0xWFU5UjJZWWhmYU8KMTFtd09iQjRJZE40TDNUS0o4R0QxRytMcncyaFAvTjJSKzY3TWVxak53NWwwV1hHS2hVMzVaWDI0dzlKakdhZgpya1JzZXExTjNIeGx4Y2ZIMjE1RExLRlVRdG5MVENzTndKbyszdDFvU1NLL2JTc1VOSmk1bGQ2aENUSW9LRDlXClZGaTlsOERBcFZqSDRqdUErMHRpZWNWaDVDakJrYnR6VFlsV0hiaENLWjE0TC9zSjZVNkp4ZGJLMzA5TmxiK0cKYmxzU1U5SEVXYlo3czgwakI4ZVMxQ0VINzQzMHNUaUFKc0FrMzAxYXlXcnQrMCt3b0VlbFFwaDNabW44QndpRgpyMzhXaVdDbXB1WjV0anNrWW92ZUtpRG9VcTBSbDJFbkRVWnc2eGNibjdwRnk4SGJEZWVvT1pObTY0MXlISjlHCmZ3NFB0UDB0QWdNQkFBRUNnZ0VBR1JLUDFUNW1HdDVhL2NVU2IyWEFXaFFaNUg0Nmwzd2lUZGExQ0s0V3h3UWkKOHZsL0dWN1V6OEd2ZTNzVU1CdDZtV3I2M2t1UTFYbmdIZStnNUg1bnRENG00L1kvckJFSUNzVHR3bmlrWENRbwpxUGhJYVNXeWlFOWJkMzhQWk52RFRnUXdTaG5zRDhwTlptM1FwdlJxWGFwZm9hZnBqTEkxMmduNXhTKytaV01PCjdLSU9PN0E4ZmVIYnVzd0MwQzRGTjBCbzE4UncydmN2UmphcTFwTnRhR2xxcmprWEVWUnhxRHVmOXF1c3Q4RmMKenpvamdyZ3R1NExKd0ZpdVNOcTdGU2UvMTJ6TzJHeGtYOG1vV084OFlSd1NZYSsxbTQ5S3NoZUU4ZTk5UEk4LwpOcDE1OEZNVVFCY2Q1RkFaRjZ6bG4waHYyUXdWakdqUitTVENZdWRPNlFLQmdRRDlRcFNXT2ZnbnBpYWJTSVF3ClFlWnpRejBvR1RVd0IrQngvOEliQkhQLzBac0hIRGNEbnZJYWM2ZVZQT09Fb3d4SUpJRzV5Q3lSWVV4NVNHMDIKbVZUZHZQOFNNait6S0NaWlo2bEJBZVVObFdYeHNXTElUT3Y3UXpRdlBXSU5YWXpSejFGVzN6V0VtRFNQZXIveAp3aW16Z1VXNVhNOVhFK2RuQnV2NHBYN3gvd0tCZ1FEUERNSU1oUy9WdWtEZDZicEs3MHpwaUJmM0lRK2xwRXQ1ClpJZFRleUlpWDBpejJpcFpQRHZnS09YekxldzFIdWE1VW8waGhpRlQ0d0ZXNW1NUFo0MnFYNmNmYVZHb3o0L2IKVjRkN2xxcG80bVJsSHc5L2dKRys5aWkzR0NFNGYxN2hHelRkazdnYllTcENHM2NlN1gyNVdSM2dQemJmTzFrVApyWldtMFZ4NDB3S0JnRWRrL0pRNDlVN2dGT1FUbGtnd1c3SEVrN241R1RoWUVCcXkrZG81OENWK3hsQkQwUEp4ClhWaTluOUYvWDdnbGFySHZzSzVaMHM2TStrejZjT2RDWkYwNkNVSHM3bTRuOUYraHpHSHFFZE01ZVlxZjhmUDcKVTA3NnkveEJOcUlEN0UyOVB2WFphTEhmWW5uTUpjNFdhVUVVUVQ2Vy9sQlM2Um52SnBocXR4V3JBb0dBQXBaagpPbXJUclRVVnFIQktUck5zMzZJK3dtemNXREtVYXVEeHUvNVc3OTBHK0pCcVpSRVdvbmVBWUNpYndoSXZ5Zk1aCkpta1pzNFdydDUxTGNaN1dxMkZrb2tUYnEyTmtwZFlUTUYweXBmcm1URWsyRlY3UzgzTDZFVWV3NnBiVTViVkUKVk55S3VYVGVaVk1ZaXY5bXlkRXVTV1lnMW10VWNxV0JzRUwvaDljQ2dZRUE1RXZWeG55RjhzZUk3Mm05ditJTQpyWnYyS1ZacjdoTzlMMkkrTW9qa2lDUUM5elhmZmRydmxUQ0hPekhWQmtScUhFby9mYTVMSHI2N1BvVXdROVYzCmFGYmg4dmJLNlpacDVaOWswYmVQNk9MbnFnQzc0S1JmMEhHZm1lTE1seXpDV0FsYk5tZDFkZEs3emtXZkgvY3YKOE5oejRnVkN5OWFSRGxldWVaTWVWanM9Ci0tLS0tRU5EIFBSSVZBVEUgS0VZLS0tLS0K";
    private static final String PUBLIC_KEY =
            "LS0tLS1CRUdJTiBQVUJMSUMgS0VZLS0tLS0KTUlJQklqQU5CZ2txaGtpRzl3MEJBUUVGQUFPQ0FROEFNSUlCQ2dLQ0FRRUF6TlYxUFVkbUdJWDJqdGRac0RtdwplQ0hUZUM5MHlpZkJnOVJ2aTY4Tm9UL3pka2Z1dXpIcW96Y09aZEZseGlvVk4rV1Y5dU1QU1l4bW42NUViSHF0ClRkeDhaY1hIeDl0ZVF5eWhWRUxaeTB3ckRjQ2FQdDdkYUVraXYyMHJGRFNZdVpYZW9Ra3lLQ2cvVmxSWXZaZkEKd0tWWXgrSTdnUHRMWW5uRlllUW93Wkc3YzAySlZoMjRRaW1kZUMvN0NlbE9pY1hXeXQ5UFRaVy9obTViRWxQUgp4Rm0yZTdQTkl3ZkhrdFFoQisrTjlMRTRnQ2JBSk45TldzbHE3ZnRQc0tCSHBVS1lkMlpwL0FjSWhhOS9Gb2xnCnBxYm1lYlk3SkdLTDNpb2c2Rkt0RVpkaEp3MUdjT3NYRzUrNlJjdkIydzNucURtVFp1dU5jaHlmUm44T0Q3VDkKTFFJREFRQUIKLS0tLS1FTkQgUFVCTElDIEtFWS0tLS0t";

    // A second, unrelated RSA key pair — used only by the "signed with a different
    // key" test to prove cross-key signatures are rejected.
    private static final String OTHER_PRIVATE_KEY =
            "LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUV2Z0lCQURBTkJna3Foa2lHOXcwQkFRRUZBQVNDQktnd2dnU2tBZ0VBQW9JQkFRRFAzRWZFa0R0NWRpSDgKeFZ0c0tFbUF6RGREcmRSUzJ3bzluMkFDTUpVUTVobHhVRmlPMjcreFh2UDRieUZzU01RV25ndjVVZUNrNFdjbgpBQm1GbWFpdllXb0dZWlhPWGtWZjJEOUxMcTdPcndYaENjbjAxQUJQTVZHZEZ5bmFmS1A0UTBTM21KVXFHMTF0CmowNUFtV3dXSElXTmc3NkNJekVHY1FhWXhSL2R6WmtIcDd1M0NjZUxYTERRRlcvZjVrR0NCZ2JCejV1cjFHL3YKOWFEWTJHWTQ3dCsrRnJMcXhadjI4Zlc0ZVJBb0NaM2haaXdSbSsxQ1dWOWJUQjRDNmJXVG9BODZXUm5RQ011VwpHY1hTQkcwTUs2YmJFb0gydGJkcFpQeFl4SVdYbExCa2pGeldRUGo1VDVRcmlwbzlDZDM5M3NOeWVncmp5Tkh1CkQ2ZXIzNFJOQWdNQkFBRUNnZ0VBS09MMFpnU1dBbFlicU9BOFU3blgvWU1PRXR6QVVWei9ObkJoWHdUbDZZbE4KYzJYaHlaWU5ycXJXRkNYdC9lSVJXajZUN2VLdWJOaGVwVVFHZ1NwZ3pVQzg3WXpuL2d3Nm1yZ0xlVE14VlB3RwpETVpUYUxxRDVnLzJOUStQbnNmYUxCVTFVT1R0WUhjNkNUbEJaQUVaeXY2b3daV3ExV2Z0Tm81b2hRZnZkUDRuCm9JLy9neDdXWVFDWVhRanRZRklZWWo2TzZFSDlSZVV5NElqeVQxMHVpNHlvT2VOM3p5MC92YUhuVC9nVWdzN3UKUXNQZEo2YmtMTmdjMnZBUkNSalNmQXF2cXk5Q1Qrb2NrRC9lOWFUS1pJMDhtaTQ0eDdzSGpYTmZXcGJ3aGJyaApCdXUwK2d4bm9UeFhFOHhOeEQwdTZ1UUJRa3hjcmFHS3loakJiV0hoU1FLQmdRRHVvT1pwallxTC9vdEpwZEVrCjcyNHVOSmZZaExqa3I1MzNCc01zcUptVkR0SHh6b0p4NjNObk9qNnFFUVNCdTBGam1pc0JuSTFzd0xIN3hlMDkKWUdNQk1EM3RIMmVhdTBkT2RDYVl6SVBiY2ZmWUlmTk8rWDcrbTcvSzF4UFF0V2pKZno1K0ZUVW5pZlVmaGw2agpEV2JNUjgycFhNL085Z3YxUVdJOS9RZTE2UUtCZ1FEZS9md0x1aE1IYk1SWlJzbWlTV1FFOENXRlJGTC8yUnZOClQwREk2SVdZSkRJMlV1ckVwK2MwbUhJcE5RMlNKOU1zV1VBSzdUMHNON2NYMHBOckg5Tm11RG1mY0hoWEZuU0oKcmxJT0ovOE9hbXpsdnVPbmlnaWVzSnFUOWVMVHVHTG5tWVpVWm5sbCttd3JDWVRORFBTS1RoV3FscUlXYjZNbApuSjJ4eEM5SXhRS0JnUURaYitta1FxSDlJR3RCSjRQa2VQdFh4UHFjQTR3S2JXK3QrUTU5TWdBSUQ2SUVDUjFaCnVxYkVhQkZUbkFBVVNsR3g5WGU4bHk5UzZsOER3UDJFME1CR0EvUlpqaVUwbS9QRXJCZkRZWS9BdFIrV1pKRTAKNUNqd3pYQzgzckFpbkRxb2FIYkVJb1QxeTBKOWdFM1ptMHVSVnRneXUreHJkRTIvSTkzbUNCc3ZpUUtCZ0cwbwpKVVpVU3RaYW51OFk0TUVwYmVXZzdLMEUyaUJWMWU4MXVYL2ZtdTN6NGdTSHFGYWwzbDczdFFLSTd1QzV6L3lvCm45bjVjZldBUElkVDFFZ2ZKeXZrU0lqTTFJdkUrVDBnY1JodTZjTFR4QVRlNGEvMHVPMTlnOTJrQXVvakczOUYKdnVUMzJMdGJ6N0Z0a20yUnh1OGc5Q2d5WHB2ZkFCejhRcEZ2ZUxvRkFvR0JBTW9VdXIwMmpEZ3pOdVNrcFI0OAoyK1VwNW4rNjV6bDNlazZGNW9tZTlKNU1PNm1XSjBNYnArTi9kUDZ6ekJ1NEFXNnJWajR3WlBFQlpBQkNPODlxCm92TEFNRTRXUHdTMnZnUmVoMnpmOXZhVlF2T0FhRGtPcUdlNjB3c0pUZGlrM1c5by9YQm5YbkVxeUtpNUYrUjQKTVRhejM5WXNpS1hHM1E4bVN5M2w4L1dDCi0tLS0tRU5EIFBSSVZBVEUgS0VZLS0tLS0K";
    private static final String OTHER_PUBLIC_KEY =
            "LS0tLS1CRUdJTiBQVUJMSUMgS0VZLS0tLS0KTUlJQklqQU5CZ2txaGtpRzl3MEJBUUVGQUFPQ0FROEFNSUlCQ2dLQ0FRRUF6OXhIeEpBN2VYWWgvTVZiYkNoSgpnTXczUTYzVVV0c0tQWjlnQWpDVkVPWVpjVkJZanR1L3NWN3orRzhoYkVqRUZwNEwrVkhncE9Gbkp3QVpoWm1vCnIyRnFCbUdWemw1Rlg5Zy9TeTZ1enE4RjRRbko5TlFBVHpGUm5SY3AybnlqK0VORXQ1aVZLaHRkYlk5T1FKbHMKRmh5RmpZTytnaU14Qm5FR21NVWYzYzJaQjZlN3R3bkhpMXl3MEJWdjMrWkJnZ1lHd2MrYnE5UnY3L1dnMk5obQpPTzdmdmhheTZzV2I5dkgxdUhrUUtBbWQ0V1lzRVp2dFFsbGZXMHdlQXVtMWs2QVBPbGtaMEFqTGxobkYwZ1J0CkRDdW0yeEtCOXJXM2FXVDhXTVNGbDVTd1pJeGMxa0Q0K1UrVUs0cWFQUW5kL2Q3RGNub0s0OGpSN2crbnE5K0UKVFFJREFRQUIKLS0tLS1FTkQgUFVCTElDIEtFWS0tLS0t";

    private JwtService jwtService;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtService = newInitializedService(PRIVATE_KEY, PUBLIC_KEY, 60_000L);
        userDetails = User.withUsername("jane.doe")
                .password("irrelevant-for-this-test")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_STAFF")))
                .build();
    }

    private static JwtService newInitializedService(String privateKey, String publicKey, long expirationMs) {
        JwtService service = new JwtService(new JwtProperties(privateKey, publicKey, "test-key-1", expirationMs));
        service.init(); // no Spring context here, so @PostConstruct won't fire on its own
        return service;
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
        JwtService shortLivedService = newInitializedService(PRIVATE_KEY, PUBLIC_KEY, 1L); // 1ms expiry
        String token = shortLivedService.generateToken(userDetails);

        Thread.sleep(20);

        assertThat(shortLivedService.isTokenValid(token, userDetails)).isFalse();
    }

    @Test
    void isTokenValid_false_whenTokenIsGarbage() {
        assertThat(jwtService.isTokenValid("not-a-real-token", userDetails)).isFalse();
    }

    @Test
    void isTokenValid_false_whenSignedWithADifferentKeyPair() {
        JwtService otherService = newInitializedService(OTHER_PRIVATE_KEY, OTHER_PUBLIC_KEY, 60_000L);
        String token = otherService.generateToken(userDetails);

        assertThat(jwtService.isTokenValid(token, userDetails)).isFalse();
    }

    @Test
    void getPublicKey_matchesConfiguredKeyId() {
        assertThat(jwtService.getKeyId()).isEqualTo("test-key-1");
        assertThat(jwtService.getPublicKey()).isNotNull();
    }
}
