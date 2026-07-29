package com.giri.oms.customerclient.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mirrors productclient's own AuthHeaderForwardingInterceptorTest exactly —
 * see that class for the reasoning. Own copy of the interceptor under test,
 * deliberately not shared (see customerclient's package-info).
 */
class AuthHeaderForwardingInterceptorTest {

    private final AuthHeaderForwardingInterceptor interceptor = new AuthHeaderForwardingInterceptor();

    @AfterEach
    void tearDown() {
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
        // No inbound servlet request bound — simulates a future async call
        // site (Kafka consumer, scheduled job), this interceptor's known
        // limitation per its own Javadoc.
        MockClientHttpRequest outboundRequest = new MockClientHttpRequest();
        var execution = mock(org.springframework.http.client.ClientHttpRequestExecution.class);
        when(execution.execute(outboundRequest, new byte[0]))
                .thenReturn(new org.springframework.mock.http.client.MockClientHttpResponse(new byte[0], 200));

        interceptor.intercept(outboundRequest, new byte[0], execution);

        assertThat(outboundRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
    }
}
