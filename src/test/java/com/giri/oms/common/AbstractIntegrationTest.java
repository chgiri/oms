package com.giri.oms.common;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Extend this class for any test that needs a real Postgres instance rather
 * than mocks — e.g. @DataJpaTest classes exercising native queries.
 *
 * IMPORTANT: the container is started once, manually, in a static initializer —
 * NOT via @Container/@Testcontainers. That annotation pair ties the container's
 * stop() call to each individual test class's lifecycle (@AfterAll), which breaks
 * the moment a second test class extends this one: Spring's test context caching
 * reuses the first class's cached DataSource (pointing at the now-stopped
 * container's port) instead of reconnecting, causing "Connection refused" on a
 * port that genuinely used to work.
 *
 * Starting the container manually here means it stays alive for the entire test
 * JVM run, shared cleanly across every test class that extends this one. The
 * Testcontainers "Ryuk" reaper container still cleans it up automatically when
 * the JVM exits — no container leaks.
 *
 * Requires Docker to be running wherever these tests execute (local machine, CI).
 */
@Tag("integration")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;
    static final GenericContainer<?> REDIS;
    public static final KafkaContainer KAFKA;

    static {
        POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
        POSTGRES.start();

        // Redisson (distributed locks, rate limiting) connects eagerly at application
        // startup — unlike the Lettuce connection factory backing Spring Cache, it's
        // not lazy — so a full @SpringBootTest context now needs a real Redis to come
        // up at all, not just for tests that specifically exercise caching/locking.
        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withCommand("redis-server --requirepass my_secret_test_password")
                .withExposedPorts(6379);
        REDIS.start();

        KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));
        KAFKA.start();
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // FORCE the autoconfiguration framework to bind an absent password
        registry.add("spring.data.redis.password", () -> "my_secret_test_password");

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.kafka.outbox.poll-interval-ms", () -> 100000L);
    }

}
