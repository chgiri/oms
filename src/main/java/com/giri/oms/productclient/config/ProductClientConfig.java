package com.giri.oms.productclient.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Stage 2 of the microservices-prep plan: RestClient wiring for calling
 * product-service.
 * <p>
 * <b>Injects the Boot-provided {@code RestClient.Builder} bean — does NOT
 * call the static {@code RestClient.builder()} factory.</b> This matters a
 * lot more than it looks: {@code RestClientAutoConfiguration} applies
 * {@code ObservationRestClientCustomizer} to that bean (see pom.xml's
 * comment on the tracing dependencies — "outbound RestClient/WebClient calls
 * propagate traceparent automatically, no code changes needed"), which is
 * what makes every call this client makes show up as one continuous trace
 * in Tempo, spanning oms-main -> product-service, instead of two
 * disconnected traces. Calling {@code RestClient.builder()} directly bypasses
 * that customizer entirely and silently loses tracing — there's no error,
 * the client still works, it just stops showing up connected in Tempo. Boot
 * hands out a fresh, independently-customizable builder instance per
 * injection point specifically so each {@code RestClient} bean like this one
 * can add its own baseUrl/timeouts/interceptors without stepping on any
 * other client's configuration.
 * <p>
 * Timeout is built on {@link JdkClientHttpRequestFactory} (core Spring
 * Framework, stable since 6.1) rather than
 * {@code org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder}/
 * {@code ClientHttpRequestFactorySettings} — those Boot-support classes moved
 * packages across Boot versions (that's exactly what broke the first version
 * of this file: {@code package org.springframework.boot.http.client does not
 * exist} against the Boot version actually in use here). Building the
 * {@link java.net.http.HttpClient} directly avoids depending on Boot's
 * internal package layout at all — only core Spring Framework + the JDK.
 * <p>
 * This is NOT resilience4j's TimeLimiter, which is designed to wrap a
 * {@code CompletableFuture}-returning call — RestClient here is
 * synchronous/blocking, so a request-factory-level timeout is the correct
 * tool for Stage 0's "short timeout" requirement. CircuitBreaker/Retry (see
 * ProductClientImpl and the resilience4j.* properties) layer on top of that.
 * <p>
 * Note this only covers the outbound half. For the trace to actually appear
 * continuous in Tempo, product-service must ALSO extract the incoming
 * traceparent header on its end — see that repo's pom.xml/application.properties
 * for the matching micrometer-tracing-bridge-otel + OTLP export setup, added
 * as part of this same Stage 2 pass.
 */
@Configuration
public class ProductClientConfig {

    @Bean
    public RestClient productServiceRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.productclient.base-url}") String baseUrl,
            @Value("${app.productclient.connect-timeout-ms:300}") long connectTimeoutMs,
            @Value("${app.productclient.read-timeout-ms:800}") long readTimeoutMs) {

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
