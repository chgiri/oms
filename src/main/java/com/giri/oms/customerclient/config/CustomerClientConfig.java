package com.giri.oms.customerclient.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Stage 2 of the microservices-prep plan (Customer extraction): RestClient
 * wiring for calling customer-service. Identical shape to
 * {@code productclient.config.ProductClientConfig} — see that class's
 * Javadoc for the full reasoning on:
 * <ul>
 *   <li>why this injects the Boot-provided {@code RestClient.Builder} bean
 *       rather than calling the static {@code RestClient.builder()} factory
 *       (tracing continuity via {@code ObservationRestClientCustomizer})</li>
 *   <li>why the timeout is built on {@link JdkClientHttpRequestFactory}
 *       rather than Boot's {@code ClientHttpRequestFactoryBuilder}/
 *       {@code ClientHttpRequestFactorySettings}</li>
 *   <li>why this is a request-factory-level timeout and not resilience4j's
 *       TimeLimiter</li>
 * </ul>
 * For the trace to appear continuous in Tempo, customer-service must also
 * extract the incoming traceparent header on its end — see that repo's
 * pom.xml/application.properties (already retrofitted proactively, ahead of
 * this client actually existing — see that repo's README).
 */
@Configuration
public class CustomerClientConfig {

    @Bean
    public RestClient customerServiceRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.customerclient.base-url}") String baseUrl,
            @Value("${app.customerclient.connect-timeout-ms:300}") long connectTimeoutMs,
            @Value("${app.customerclient.read-timeout-ms:800}") long readTimeoutMs) {

        HttpClient jdkHttpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkHttpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(new AuthHeaderForwardingInterceptor())
                .build();
    }
}
