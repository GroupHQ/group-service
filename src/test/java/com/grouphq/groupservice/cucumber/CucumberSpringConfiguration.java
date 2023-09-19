package com.grouphq.groupservice.cucumber;

import io.cucumber.spring.CucumberContextConfiguration;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Configuration for linking Cucumber with the Spring context.
 */
@CucumberContextConfiguration
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"group.loader.enabled=true"})
@AutoConfigureWebTestClient
@ContextConfiguration(initializers = CucumberSpringConfiguration.Initializer.class)
public class CucumberSpringConfiguration {
    static class Initializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.4"));

        static {
            POSTGRESQL_CONTAINER.start();
        }

        @Override
        public void initialize(
            @NotNull ConfigurableApplicationContext configurableApplicationContext) {
            final TestPropertyValues values = TestPropertyValues.of(
                "spring.r2dbc.url=" + r2dbcUrl(),
                "spring.r2dbc.username=" + POSTGRESQL_CONTAINER.getUsername(),
                "spring.r2dbc.password=" + POSTGRESQL_CONTAINER.getPassword(),
                "spring.flyway.url=" + POSTGRESQL_CONTAINER.getJdbcUrl(),
                "spring.flyway.user=" + POSTGRESQL_CONTAINER.getUsername(),
                "spring.flyway.password=" + POSTGRESQL_CONTAINER.getPassword()
            );
            values.applyTo(configurableApplicationContext);
        }

        private static String r2dbcUrl() {
            return String.format("r2dbc:postgresql://%s:%s/%s", POSTGRESQL_CONTAINER.getHost(),
                POSTGRESQL_CONTAINER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                POSTGRESQL_CONTAINER.getDatabaseName());
        }
    }
}
