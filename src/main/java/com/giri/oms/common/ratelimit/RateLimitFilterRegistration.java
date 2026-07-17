package com.giri.oms.common.ratelimit;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@RequiredArgsConstructor
public class RateLimitFilterRegistration {

    private final ProxyManager<String> rateLimitProxyManager;
    private final RateLimitProperties rateLimitProperties;
    private final JsonMapper objectMapper;

    @Bean
    public FilterRegistrationBean<LoginRateLimitFilter> loginRateLimitFilterRegistration() {
        FilterRegistrationBean<LoginRateLimitFilter> registration = new FilterRegistrationBean<>(
                new LoginRateLimitFilter(rateLimitProxyManager, rateLimitProperties, objectMapper));
        registration.addUrlPatterns("/api/auth/login");
        registration.setOrder(1); // run before Spring Security's filter chain
        return registration;
    }
}