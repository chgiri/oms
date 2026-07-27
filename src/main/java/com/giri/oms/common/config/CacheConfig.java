package com.giri.oms.common.config;

import com.giri.oms.inventory.dto.InventoryResponse;
import com.giri.oms.product.dto.ProductResponse;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.support.collections.RedisProperties;

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
 *
 * Each cache is bound to its own {@link JacksonJsonRedisSerializer}, typed to the exact
 * DTO it stores, rather than sharing one polymorphic {@code GenericJacksonJsonRedisSerializer}
 * across caches. A generic/polymorphic serializer only knows the value as Object, so it has
 * to embed a "@class" type id in the JSON to know what to deserialize back into — and every
 * JDK value type whose JSON form is ambiguous (BigDecimal, LocalDateTime, UUID, ...) needs
 * that type id explicitly allow-listed via a PolymorphicTypeValidator, or read-back throws
 * SerializationException ("Could not resolve type id ..."). That's a maintenance trap: every
 * new field type on a cached DTO risks re-triggering it. Since we already configure each
 * cache's serializer separately below, we already know the concrete type per cache at
 * config time — so there's nothing polymorphic to resolve, no allowlist to maintain, and no
 * type id written into Redis at all.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PRODUCTS_CACHE = "products";
    public static final String INVENTORY_CACHE = "inventory";

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .disableCachingNullValues();

        RedisCacheConfiguration productsConfig = base
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(ProductResponse.class)))
                // Products change rarely (price/name edits) — safe to cache longer.
                .entryTtl(Duration.ofMinutes(15));

        RedisCacheConfiguration inventoryConfig = base
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(InventoryResponse.class)))
                // Inventory quantities move more often — shorter TTL so stale stock
                // levels don't linger for long between explicit evictions.
                .entryTtl(Duration.ofMinutes(2));

        return builder -> builder
                .withCacheConfiguration(PRODUCTS_CACHE, productsConfig)
                .withCacheConfiguration(INVENTORY_CACHE, inventoryConfig);
    }
}