package com.giri.oms.productclient.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Isolates the token-forwarding logic (Stage 2's chosen auth strategy — see
 * the Stage 0/2 decision log) from the rest of ProductClientImpl, so these
 * three scenarios don't need a real HTTP call or WireMock to verify.
 */
class AuthHeaderForwardingInterceptorTest {

    private final AuthHeaderForwardingInterceptor interceptor = new AuthHeaderForwardingInterceptor();

    @AfterEach
    void tearDown() {
        // Every test that binds request attributes must clear them, or a
        // later test (including one in a different class, if the JUnit
        // engine reuses this thread) would see a stale bound request.
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void forwardsAuthorizationHeader_whenPresentOnInboundRequest() throws Exception {
        MockHttpServletRequest inboundRequest = new MockHttpServletRequest();
        inboundRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer the-users-actual-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inboundRequest));

        MockClientHttpRequest outboundRequest = new MockClientHttpRequest();
        var execution = mock(org.springframework.http.client.ClientHttpRequestExecution.class);
        when(execution.execute(outboundRequest, new byte[0]))
                .thenReturn(new org.springframework.mock.http.client.MockClientHttpResponse(new byte[0], 200));

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertThat(outboundRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer the-users-actual-token");
    }

    @Test
    void setsNoAuthorizationHeader_whenInboundRequestHasNone() throws Exception {
        MockHttpServletRequest inboundRequest = new MockHttpServletRequest(); // no Authorization header set
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inboundRequest));

        MockClientHttpRequest outboundRequest = new MockClientHttpRequest();
        var execution = mock(org.springframework.http.client.ClientHttpRequestExecution.class);
        when(execution.execute(outboundRequest, new byte[0]))
                .thenReturn(new org.springframework.mock.http.client.MockClientHttpResponse(new byte[0], 200));

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertThat(outboundRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    void setsNoAuthorizationHeader_andDoesNotThrow_whenNoRequestIsBound() throws Exception {
        // The scenario the class Javadoc calls out as this interceptor's known
        // limitation — a call site with no live inbound servlet request
        // (a Kafka consumer, a scheduled job). RequestContextHolder is
        // deliberately left unbound here (no setRequestAttributes call) to
        // simulate exactly that.
        MockClientHttpRequest outboundRequest = new MockClientHttpRequest();
        var execution = mock(org.springframework.http.client.ClientHttpRequestExecution.class);
        when(execution.execute(outboundRequest, new byte[0]))
                .thenReturn(new org.springframework.mock.http.client.MockClientHttpResponse(new byte[0], 200));

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertThat(outboundRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
    }
}
