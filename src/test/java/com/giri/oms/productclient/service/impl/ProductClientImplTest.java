package com.giri.oms.productclient.service.impl;

import com.giri.oms.productclient.exception.ProductNotFoundException;
import com.giri.oms.productclient.dto.ProductClientResponse;
import com.giri.oms.productclient.exception.ProductServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Deliberately no Spring context — none of the beans in
 * productclient.config are exercised here (see AuthHeaderForwardingInterceptorTest
 * for that piece); this is a plain unit test wiring a real RestClient at a
 * real embedded HTTP server (WireMock) plus resilience4j registries built
 * with the same values as application.properties'
 * resilience4j.*.instances.productClient config (retry wait/circuit-breaker
 * cooldown shortened here purely so the test runs fast — the *rules*,
 * not the timings, are what's under test).
 * <p>
 * Using a real embedded server rather than a mocked RestClient is the
 * point: this exercises real HTTP status handling and real JSON
 * deserialization, which is exactly the layer that would have caught a
 * contract drift (a renamed/missing field, an unexpected status code)
 * immediately — see the "stock column" incident during Phase 1's test
 * fixes for a concrete example of the kind of mismatch this class of test
 * catches that a mocked client never would.
 */
class ProductClientImplTest {

    private WireMockServer wireMockServer;
    private ProductClientImpl productClient;
    private CircuitBreaker circuitBreaker;

    private static final Long PRODUCT_ID = 42L;
    private static final String PRODUCT_PATH = "/products/42";

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        RestClient restClient = RestClient.builder()
                .baseUrl(wireMockServer.baseUrl())
                .requestFactory(shortTimeoutRequestFactory())
                .build();

        // Fresh registries per test — a CircuitBreaker/Retry instance carries
        // state (the sliding window, the open/closed status) across calls, so
        // sharing one across test methods would let an earlier test's
        // failures leak into a later test's assertions.
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60)) // long on purpose — these tests only assert "open blocks calls", never the half-open recovery path, which is resilience4j's own well-tested behavior, not this class's
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                .ignoreExceptions(ProductNotFoundException.class)
                .build();
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("productClient");

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(2) // matches resilience4j.retry.instances.productClient.max-attempts in application.properties
                .waitDuration(Duration.ofMillis(10)) // shortened from the real 200ms purely for test speed
                .retryExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                .ignoreExceptions(ProductNotFoundException.class)
                .build();
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);

        productClient = new ProductClientImpl(restClient, circuitBreakerRegistry, retryRegistry);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private static ClientHttpRequestFactory shortTimeoutRequestFactory() {
        // Same JdkClientHttpRequestFactory approach as ProductClientConfig —
        // see that class's Javadoc for why the org.springframework.boot.http.client
        // Boot-support classes were dropped in favor of this.
        HttpClient jdkHttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(300))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkHttpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(200)); // short on purpose — see TimeoutHandling below, which relies on this to trigger ResourceAccessException without a slow test
        return requestFactory;
    }

    @Nested
    class SuccessfulResponse {

        @Test
        void returnsDeserializedProduct_whenProductServiceRespondsSuccessfully() {
            wireMockServer.stubFor(get(urlEqualTo(PRODUCT_PATH))
                    .willReturn(okJson("""
                            {"id": 42, "name": "Wireless Mouse", "price": 25.99}
                            """)));

            ProductClientResponse response = productClient.getProduct(PRODUCT_ID);

            assertThat(response.id()).isEqualTo(42L);
            assertThat(response.name()).isEqualTo("Wireless Mouse");
            assertThat(response.price()).isEqualByComparingTo("25.99");
        }
    }

    @Nested
    class NotFoundHandling {

        @Test
        void throwsProductNotFoundException_on404_andDoesNotRetry() {
            wireMockServer.stubFor(get(urlEqualTo(PRODUCT_PATH))
                    .willReturn(aResponse().withStatus(404)));

            assertThatThrownBy(() -> productClient.getProduct(PRODUCT_ID))
                    .isInstanceOf(ProductNotFoundException.class);

            // Exactly 1 request — a 404 is a legitimate business rejection
            // (Stage 0's resilience decision), never retried.
            wireMockServer.verify(1, getRequestedFor(urlEqualTo(PRODUCT_PATH)));
        }

        @Test
        void repeated404s_neverOpenTheCircuitBreaker() {
            wireMockServer.stubFor(get(urlEqualTo(PRODUCT_PATH))
                    .willReturn(aResponse().withStatus(404)));

            // More than the sliding window size (4) worth of 404s — if these
            // counted as failures, the circuit would be open by now.
            for (int i = 0; i < 6; i++) {
                assertThatThrownBy(() -> productClient.getProduct(PRODUCT_ID))
                        .isInstanceOf(ProductNotFoundException.class);
            }

            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }
    }

    @Nested
    class ServerErrorHandling {

        @Test
        void throwsProductServiceUnavailableException_on500_afterOneRetry() {
            wireMockServer.stubFor(get(urlEqualTo(PRODUCT_PATH))
                    .willReturn(aResponse().withStatus(500)));

            assertThatThrownBy(() -> productClient.getProduct(PRODUCT_ID))
                    .isInstanceOf(ProductServiceUnavailableException.class)
                    .hasMessageContaining("42");

            // max-attempts=2: the original call plus exactly 1 retry.
            wireMockServer.verify(2, getRequestedFor(urlEqualTo(PRODUCT_PATH)));
        }

        @Test
        void succeedsOnRetry_whenFirstCallFailsButSecondSucceeds() {
            wireMockServer.stubFor(get(urlEqualTo(PRODUCT_PATH))
                    .inScenario("flaky-product-service")
                    .whenScenarioStateIs(Scenario.STARTED)
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo("recovered"));
            wireMockServer.stubFor(get(urlEqualTo(PRODUCT_PATH))
                    .inScenario("flaky-product-service")
                    .whenScenarioStateIs("recovered")
                    .willReturn(okJson("""
                            {"id": 42, "name": "Wireless Mouse", "price": 25.99}
                            """)));

            ProductClientResponse response = productClient.getProduct(PRODUCT_ID);

            assertThat(response.name()).isEqualTo("Wireless Mouse");
            wireMockServer.verify(2, getRequestedFor(urlEqualTo(PRODUCT_PATH)));
        }
    }

    @Nested
    class TimeoutHandling {

        @Test
        void throwsProductServiceUnavailableException_whenProductServiceIsSlow() {
            // Longer than the 200ms read timeout configured in setUp() —
            // triggers a real java.net.SocketTimeoutException, wrapped by
            // RestClient as ResourceAccessException, which is exactly what
            // resilience4j.retry/circuitbreaker.instances.productClient.retry-exceptions
            // (application.properties) is configured to treat as retryable.
            wireMockServer.stubFor(get(urlEqualTo(PRODUCT_PATH))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(600)));

            assertThatThrownBy(() -> productClient.getProduct(PRODUCT_ID))
                    .isInstanceOf(ProductServiceUnavailableException.class);
        }
    }

    @Nested
    class CircuitBreakerBehavior {

        @Test
        void opensAfterRepeatedServerErrors_andShortCircuitsWithoutCallingProductService() {
            wireMockServer.stubFor(get(urlEqualTo(PRODUCT_PATH))
                    .willReturn(aResponse().withStatus(500)));

            // slidingWindowSize=4, failureRateThreshold=50%, maxAttempts=2 —
            // each getProduct() call that fails counts as 1 failure per
            // resilience4j's decorator nesting order (CircuitBreaker wraps
            // Retry, so the circuit sees one outcome per getProduct() call,
            // not one per HTTP attempt). 4 failing calls fills the window at
            // 100% failure, well past the 50% threshold, so the circuit
            // should be open by the 5th call.
            for (int i = 0; i < 4; i++) {
                assertThatThrownBy(() -> productClient.getProduct(PRODUCT_ID))
                        .isInstanceOf(ProductServiceUnavailableException.class);
            }
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            wireMockServer.resetRequests();

            assertThatThrownBy(() -> productClient.getProduct(PRODUCT_ID))
                    .isInstanceOf(ProductServiceUnavailableException.class);

            // The whole point of an open circuit: this call never reached
            // WireMock at all — resilience4j's CallNotPermittedException
            // short-circuited it before doGetProduct() ran.
            wireMockServer.verify(0, getRequestedFor(urlEqualTo(PRODUCT_PATH)));
        }
    }
}
