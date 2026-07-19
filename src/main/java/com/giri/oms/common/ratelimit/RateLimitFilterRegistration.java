package com.giri.oms.common.ratelimit;

import com.giri.oms.common.config.WebConfig;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

@Configuration
@RequiredArgsConstructor
public class RateLimitFilterRegistration {

    private final ProxyManager<String> rateLimitProxyManager;
    private final RateLimitProperties rateLimitProperties;
    private final JsonMapper objectMapper;
    private final Clock clock;

    @Bean
    public FilterRegistrationBean<LoginRateLimitFilter> loginRateLimitFilterRegistration() {
        FilterRegistrationBean<LoginRateLimitFilter> registration = new FilterRegistrationBean<>(
                new LoginRateLimitFilter(rateLimitProxyManager, rateLimitProperties, objectMapper, clock));
        registration.addUrlPatterns(WebConfig.API_PREFIX + "/auth/login");
        registration.setOrder(1); // run before Spring Security's filter chain
        return registration;
    }
}