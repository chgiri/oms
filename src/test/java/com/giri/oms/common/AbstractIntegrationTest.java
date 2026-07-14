package com.giri.oms.common;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Extend this class for any test that needs a real Postgres instance rather
 * than mocks — e.g. @DataJpaTest classes exercising native queries.
 *
 * @ServiceConnection auto-wires Spring's datasource properties to point at
 * the container — no manual spring.datasource.url wiring needed.
 *
 * Requires Docker to be running wherever these tests execute (local machine, CI).
 */
@Testcontainers
@Tag("integration")
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

}
