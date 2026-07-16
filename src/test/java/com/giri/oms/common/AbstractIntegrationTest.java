package com.giri.oms.common;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
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

    static {
        POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

}
