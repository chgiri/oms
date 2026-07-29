package com.giri.oms.customerclient.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;

/**
 * Own copy of {@code productclient.config.AuthHeaderForwardingInterceptor} —
 * identical behavior, deliberately not shared (see {@code customerclient}'s
 * package-info for why). See that class's Javadoc for the full reasoning on
 * the token-forwarding strategy and its scope/limitation (only works on a
 * thread handling an inbound servlet request — a future async call site,
 * e.g. from a Kafka consumer, would forward nothing).
 */
public class AuthHeaderForwardingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String incomingAuthHeader = currentAuthorizationHeader();
        if (incomingAuthHeader != null) {
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, incomingAuthHeader);
        }
        return execution.execute(request, body);
    }

    private String currentAuthorizationHeader() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest currentRequest = attributes.getRequest();
        return currentRequest.getHeader(HttpHeaders.AUTHORIZATION);
    }
}
