package com.giri.oms.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Token-bucket settings for the login endpoint: {@code capacity} tokens available up
 * front, refilling by {@code refillTokens} every {@code refillDurationSeconds} — e.g.
 * the defaults (5 / 5 / 60) allow 5 login attempts per client IP, replenishing to 5
 * again over the following minute.
 */
@ConfigurationProperties(prefix = "app.ratelimit.login")
public record RateLimitProperties(int capacity, int refillTokens, long refillDurationSeconds) {
}
