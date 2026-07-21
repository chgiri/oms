package com.giri.oms.common.config;

import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.support.collections.RedisProperties;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;

/**
 * Backs Spring's cache abstraction ({@code @Cacheable}/{@code @CacheEvict}) with Redis
 * instead of the default in-memory ConcurrentHashMap. This matters for production: with
 * more than one app instance behind a load balancer, an in-memory cache goes stale on
 * every instance except the one that handled the write. Redis gives every instance the
 * same view and the same invalidation.
 *
 * Cache names/TTLs here correspond to the {@code @Cacheable} annotations on
 * ProductServiceImpl and InventoryServiceImpl — read-mostly, id-keyed lookups are what
 * benefit from caching; paginated/search results are deliberately NOT cached since their
 * key space is unbounded and they change too often to be worth it.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PRODUCTS_CACHE = "products";
    public static final String INVENTORY_CACHE = "inventory";

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        // GenericJacksonJsonRedisSerializer, unlike the deprecated GenericJackson2JsonRedisSerializer
        // it replaces, does NOT enable default typing out of the box. Without it, cached values are
        // written as plain JSON with no "@class" hint, so on read-back Spring's cache abstraction
        // (which only knows the value as Object) gets a generic LinkedHashMap instead of the real
        // DTO — surfacing as a ClassCastException at the call site (see e.g. InventoryServiceImpl
        // #getInventoryById). Enabling default typing, scoped to our own package rather than via
        // enableUnsafeDefaultTyping(), restores type-safe round-tripping without accepting arbitrary
        // classes from the cache payload.
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.giri.oms.")
                .build();

        RedisSerializationContext.SerializationPair<Object> valueSerializer =
                RedisSerializationContext.SerializationPair.fromSerializer(
                        GenericJacksonJsonRedisSerializer.builder()
                                .enableDefaultTyping(typeValidator)
                                .build());

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(valueSerializer)
                .disableCachingNullValues();

        return builder -> builder
                // Products change rarely (price/name edits) — safe to cache longer.
                .withCacheConfiguration(PRODUCTS_CACHE, base.entryTtl(Duration.ofMinutes(15)))
                // Inventory quantities move more often — shorter TTL so stale stock
                // levels don't linger for long between explicit evictions.
                .withCacheConfiguration(INVENTORY_CACHE, base.entryTtl(Duration.ofMinutes(2)));
    }
}