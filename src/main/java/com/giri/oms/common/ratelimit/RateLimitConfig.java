package com.giri.oms.common.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.redisson.Bucket4jRedisson;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Wires Bucket4j's Redisson backend on top of the same RedissonClient used for
 * distributed locking (see DistributedLockService) — one Redis connection pool serving
 * both concerns. The resulting ProxyManager hands out buckets that live in Redis, so
 * the rate limit is enforced across every app instance, not per-instance.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    public ProxyManager<String> rateLimitProxyManager(RedissonClient redissonClient) {
        Redisson redisson = (Redisson) redissonClient;
        return Bucket4jRedisson.casBasedBuilder(redisson.getCommandExecutor())
                // Buckets for IPs that stop making requests are evicted from Redis
                // instead of sitting there forever — 10 minutes comfortably outlives
                // the refill window (default 60s) so an active client's bucket is
                // never evicted mid-use.
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10)))
                .build();
    }
}
