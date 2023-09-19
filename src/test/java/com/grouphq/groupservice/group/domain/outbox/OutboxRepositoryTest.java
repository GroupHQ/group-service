package com.grouphq.groupservice.group.domain.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.grouphq.groupservice.config.DataConfig;
import com.grouphq.groupservice.group.domain.members.Member;
import com.grouphq.groupservice.group.domain.outbox.enums.AggregateType;
import com.grouphq.groupservice.group.domain.outbox.enums.EventStatus;
import com.grouphq.groupservice.group.domain.outbox.enums.EventType;
import com.grouphq.groupservice.group.testutility.GroupTestUtility;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

@DataR2dbcTest
@Import(DataConfig.class)
@Testcontainers
@Tag("IntegrationTest")
class OutboxRepositoryTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.4"));

    @Autowired
    private OutboxRepository outboxRepository;

    private final ObjectMapper objectMapper;

    @DynamicPropertySource
    private static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", OutboxRepositoryTest::r2dbcUrl);
        registry.add("spring.r2dbc.username", POSTGRESQL_CONTAINER::getUsername);
        registry.add("spring.r2dbc.password", POSTGRESQL_CONTAINER::getPassword);
        registry.add("spring.flyway.url", POSTGRESQL_CONTAINER::getJdbcUrl);
    }

    private static String r2dbcUrl() {
        return String.format("r2dbc:postgresql://%s:%s/%s", POSTGRESQL_CONTAINER.getHost(),
            POSTGRESQL_CONTAINER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
            POSTGRESQL_CONTAINER.getDatabaseName());
    }

    public OutboxRepositoryTest() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Save outbox message to database")
    void saveOutboxMessage() throws JsonProcessingException {
        final Member member = GroupTestUtility.generateFullMemberDetails("User", 1L);

        final OutboxEvent result = OutboxEvent.of(
            UUID.randomUUID(), 1L, AggregateType.GROUP, EventType.MEMBER_JOINED,
            objectMapper.writeValueAsString(member), EventStatus.SUCCESSFUL, "websocketId");

        StepVerifier.create(
            outboxRepository.save(
                result.getEventId(), result.getAggregateId(), result.getAggregateType(),
                result.getEventType(), result.getEventData(), result.getEventStatus(),
                result.getWebsocketId(), Instant.now()))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        StepVerifier.create(outboxRepository.findAll())
            .expectNextCount(1)
            .expectComplete()
            .verify(Duration.ofSeconds(10));
    }

}
