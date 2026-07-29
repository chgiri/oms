package com.giri.oms.productclient.config;

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
 * Stage 2's chosen service-to-service auth strategy (see the Stage 0/2
 * decision log): token forwarding, not a separate service-identity
 * credential. Reads the Authorization header off the CURRENT inbound
 * request — the end user's already-verified JWT — and re-sends it unchanged
 * to product-service, which validates it against the same JWKS endpoint it
 * was issued from. No new auth infrastructure needed for either service.
 * <p>
 * Deliberately a standalone {@link ClientHttpRequestInterceptor}, not logic
 * folded directly into ProductClientConfig's RestClient bean — so that if a
 * service-identity token is ever needed later (an async call site with no
 * inbound request to forward from — see this class's own limitation below),
 * that's a matter of registering a different interceptor bean, not rewriting
 * ProductClientImpl or the RestClient bean itself.
 * <p>
 * <b>Scope/limitation:</b> only works on a thread actually handling an
 * inbound servlet request. Both of Stage 4's planned call sites
 * (OrderServiceImpl.createOrder, InventoryServiceImpl create/update) are
 * exactly that — synchronous calls triggered directly by an authenticated
 * HTTP request — so this is sufficient for what exists today. It would
 * silently forward nothing (see currentAuthorizationHeader() returning null
 * below) if ProductClient were ever called from a Kafka consumer or
 * OutboxPublisher's scheduled thread, neither of which has an inbound
 * request to read from — revisit then, not before.
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
