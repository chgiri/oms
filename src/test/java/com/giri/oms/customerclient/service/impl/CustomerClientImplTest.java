package com.giri.oms.customerclient.service.impl;

import com.giri.oms.customerclient.exception.CustomerNotFoundException;
import com.giri.oms.customerclient.dto.CustomerClientResponse;
import com.giri.oms.customerclient.exception.CustomerServiceUnavailableException;
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
 * Mirrors productclient's own ProductClientImplTest exactly — same reasoning
 * for using a real embedded server (WireMock) instead of a mocked RestClient
 * (real HTTP status handling, real JSON deserialization — see that class's
 * Javadoc). Resilience4j registry values match
 * resilience4j.*.instances.customerClient in application.properties, with
 * wait/cooldown durations shortened purely for test speed.
 */
class CustomerClientImplTest {

    private WireMockServer wireMockServer;
    private CustomerClientImpl customerClient;
    private CircuitBreaker circuitBreaker;

    private static final Long CUSTOMER_ID = 42L;
    private static final String CUSTOMER_PATH = "/customers/42";

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        RestClient restClient = RestClient.builder()
                .baseUrl(wireMockServer.baseUrl())
                .requestFactory(shortTimeoutRequestFactory())
                .build();

        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .permittedNumberOfCallsInHalfOpenState(2)
                .recordExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                .ignoreExceptions(CustomerNotFoundException.class)
                .build();
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("customerClient");

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(10))
                .retryExceptions(HttpServerErrorException.class, ResourceAccessException.class)
                .ignoreExceptions(CustomerNotFoundException.class)
                .build();
        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig);

        customerClient = new CustomerClientImpl(restClient, circuitBreakerRegistry, retryRegistry);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    private static ClientHttpRequestFactory shortTimeoutRequestFactory() {
        HttpClient jdkHttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(300))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkHttpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(200));
        return requestFactory;
    }

    @Nested
    class SuccessfulResponse {

        @Test
        void returnsDeserializedCustomer_whenCustomerServiceRespondsSuccessfully() {
            wireMockServer.stubFor(get(urlEqualTo(CUSTOMER_PATH))
                    .willReturn(okJson("""
                            {"id": 42, "firstName": "Ada", "lastName": "Lovelace"}
                            """)));

            CustomerClientResponse response = customerClient.getCustomer(CUSTOMER_ID);

            assertThat(response.id()).isEqualTo(42L);
            assertThat(response.firstName()).isEqualTo("Ada");
            assertThat(response.lastName()).isEqualTo("Lovelace");
        }
    }

    @Nested
    class NotFoundHandling {

        @Test
        void throwsCustomerNotFoundException_on404_andDoesNotRetry() {
            wireMockServer.stubFor(get(urlEqualTo(CUSTOMER_PATH))
                    .willReturn(aResponse().withStatus(404)));

            assertThatThrownBy(() -> customerClient.getCustomer(CUSTOMER_ID))
                    .isInstanceOf(CustomerNotFoundException.class);

            wireMockServer.verify(1, getRequestedFor(urlEqualTo(CUSTOMER_PATH)));
        }

        @Test
        void repeated404s_neverOpenTheCircuitBreaker() {
            wireMockServer.stubFor(get(urlEqualTo(CUSTOMER_PATH))
                    .willReturn(aResponse().withStatus(404)));

            for (int i = 0; i < 6; i++) {
                assertThatThrownBy(() -> customerClient.getCustomer(CUSTOMER_ID))
                        .isInstanceOf(CustomerNotFoundException.class);
            }

            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }
    }

    @Nested
    class ServerErrorHandling {

        @Test
        void throwsCustomerServiceUnavailableException_on500_afterOneRetry() {
            wireMockServer.stubFor(get(urlEqualTo(CUSTOMER_PATH))
                    .willReturn(aResponse().withStatus(500)));

            assertThatThrownBy(() -> customerClient.getCustomer(CUSTOMER_ID))
                    .isInstanceOf(CustomerServiceUnavailableException.class)
                    .hasMessageContaining("42");

            wireMockServer.verify(2, getRequestedFor(urlEqualTo(CUSTOMER_PATH)));
        }

        @Test
        void succeedsOnRetry_whenFirstCallFailsButSecondSucceeds() {
            wireMockServer.stubFor(get(urlEqualTo(CUSTOMER_PATH))
                    .inScenario("flaky-customer-service")
                    .whenScenarioStateIs(Scenario.STARTED)
                    .willReturn(aResponse().withStatus(500))
                    .willSetStateTo("recovered"));
            wireMockServer.stubFor(get(urlEqualTo(CUSTOMER_PATH))
                    .inScenario("flaky-customer-service")
                    .whenScenarioStateIs("recovered")
                    .willReturn(okJson("""
                            {"id": 42, "firstName": "Ada", "lastName": "Lovelace"}
                            """)));

            CustomerClientResponse response = customerClient.getCustomer(CUSTOMER_ID);

            assertThat(response.firstName()).isEqualTo("Ada");
            wireMockServer.verify(2, getRequestedFor(urlEqualTo(CUSTOMER_PATH)));
        }
    }

    @Nested
    class TimeoutHandling {

        @Test
        void throwsCustomerServiceUnavailableException_whenCustomerServiceIsSlow() {
            wireMockServer.stubFor(get(urlEqualTo(CUSTOMER_PATH))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(600)));

            assertThatThrownBy(() -> customerClient.getCustomer(CUSTOMER_ID))
                    .isInstanceOf(CustomerServiceUnavailableException.class);
        }
    }

    @Nested
    class CircuitBreakerBehavior {

        @Test
        void opensAfterRepeatedServerErrors_andShortCircuitsWithoutCallingCustomerService() {
            wireMockServer.stubFor(get(urlEqualTo(CUSTOMER_PATH))
                    .willReturn(aResponse().withStatus(500)));

            for (int i = 0; i < 4; i++) {
                assertThatThrownBy(() -> customerClient.getCustomer(CUSTOMER_ID))
                        .isInstanceOf(CustomerServiceUnavailableException.class);
            }
            assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            wireMockServer.resetRequests();

            assertThatThrownBy(() -> customerClient.getCustomer(CUSTOMER_ID))
                    .isInstanceOf(CustomerServiceUnavailableException.class);

            wireMockServer.verify(0, getRequestedFor(urlEqualTo(CUSTOMER_PATH)));
        }
    }
}
