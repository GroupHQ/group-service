package org.grouphq.groupservice.group.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupRepository;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.domain.outbox.ErrorData;
import org.grouphq.groupservice.group.domain.outbox.OutboxEvent;
import org.grouphq.groupservice.group.domain.outbox.enums.AggregateType;
import org.grouphq.groupservice.group.domain.outbox.enums.EventStatus;
import org.grouphq.groupservice.group.domain.outbox.enums.EventType;
import org.grouphq.groupservice.group.event.daos.GroupCreateRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupStatusRequestEvent;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

@SpringBootTest
@Import(TestChannelBinderConfiguration.class)
@Testcontainers
@Tag("IntegrationTest")
class GroupEventGroupIntegrationTest {

    /**
     * You may see an error in your IDE for this field.
     *
     * @see GroupEventMemberIntegrationTest#inputDestination for IDE error explanation.
     */
    @Autowired
    private InputDestination inputDestination;

    @Autowired
    private OutputDestination outputDestination;

    @Autowired
    private GroupRepository groupRepository;

    @Value("${spring.cloud.stream.bindings.handleGroupCreateRequests-in-0.destination}")
    private String createHandlerDestination;

    @Value("${spring.cloud.stream.bindings.handleGroupStatusRequests-in-0.destination}")
    private String updateStatusHandlerDestination;

    @Value("${spring.cloud.stream.bindings.publishProcessedEvents-out-0.destination}")
    private String eventPublisherDestination;

    @Autowired
    private ObjectMapper objectMapper;

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.4"));

    @DynamicPropertySource
    private static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", GroupEventGroupIntegrationTest::r2dbcUrl);
        registry.add("spring.r2dbc.username", POSTGRESQL_CONTAINER::getUsername);
        registry.add("spring.r2dbc.password", POSTGRESQL_CONTAINER::getPassword);
        registry.add("spring.flyway.url", POSTGRESQL_CONTAINER::getJdbcUrl);
    }

    private static String r2dbcUrl() {
        return String.format("r2dbc:postgresql://%s:%s/%s", POSTGRESQL_CONTAINER.getHost(),
            POSTGRESQL_CONTAINER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
            POSTGRESQL_CONTAINER.getDatabaseName());
    }

    @Test
    @DisplayName("Successfully fulfills a group create request")
    void createsGroup() throws IOException {
        final GroupCreateRequestEvent requestEvent =
            GroupTestUtility.generateGroupCreateRequestEvent();

        inputDestination.send(new GenericMessage<>(requestEvent), createHandlerDestination);
        final Message<byte[]> payload = outputDestination.receive(1000, eventPublisherDestination);

        final OutboxEvent event = objectMapper.readValue(payload.getPayload(), OutboxEvent.class);

        assertThat(event).satisfies(
            actual -> assertThat(actual.getEventId()).isEqualTo(requestEvent.getEventId()),
            actual -> assertThat(actual.getAggregateId()).isNotNull(),
            actual -> assertThat(actual.getAggregateType()).isEqualTo(AggregateType.GROUP),
            actual -> assertThat(actual.getEventType()).isEqualTo(EventType.GROUP_CREATED),
            actual -> assertThat(objectMapper.readValue(actual.getEventData(), Group.class))
                .isInstanceOf(Group.class),
            actual -> assertThat(actual.getEventStatus()).isEqualTo(EventStatus.SUCCESSFUL),
            actual -> assertThat(actual.getWebsocketId()).isEqualTo(requestEvent.getWebsocketId())
        );

        // Check that the group was saved to the database
        StepVerifier.create(groupRepository.findById(event.getAggregateId()))
            .expectNextMatches(group -> group.status().equals(GroupStatus.ACTIVE))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Unsuccessfully fulfills a group create request")
    void createsGroupFailure() throws IOException {
        // Create request with invalid capacities (max exceeding current)
        final GroupCreateRequestEvent requestEvent =
            GroupTestUtility.generateGroupCreateRequestEvent(5, 10);

        inputDestination.send(new GenericMessage<>(requestEvent), createHandlerDestination);
        final Message<byte[]> payload = outputDestination.receive(1000, eventPublisherDestination);

        final OutboxEvent event = objectMapper.readValue(payload.getPayload(), OutboxEvent.class);

        final ErrorData errorData = objectMapper.readValue(event.getEventData(), ErrorData.class);

        assertThat(event).satisfies(
            actual -> assertThat(actual.getEventId()).isEqualTo(requestEvent.getEventId()),
            actual -> assertThat(actual.getAggregateId()).isNull(),
            actual -> assertThat(actual.getAggregateType()).isEqualTo(AggregateType.GROUP),
            actual -> assertThat(actual.getEventType()).isEqualTo(EventType.GROUP_CREATED),
            actual -> assertThat(actual.getEventStatus()).isEqualTo(EventStatus.FAILED),
            actual -> assertThat(actual.getWebsocketId()).isEqualTo(requestEvent.getWebsocketId())
        );

        assertThat(errorData.error()).isEqualTo("Cannot create group because this group's "
                                                + "proposed size exceeds its maximum size.");
    }

    @Test
    @DisplayName("Successfully fulfills a group status request")
    void disbandsGroup() throws IOException {
        final Group group = GroupTestUtility.generateFullGroupDetails(1000L, GroupStatus.ACTIVE);
        StepVerifier.create(groupRepository.save(group))
            .expectNextCount(1)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        final GroupStatusRequestEvent requestEvent =
            GroupTestUtility.generateGroupStatusRequestEvent(1000L, GroupStatus.AUTO_DISBANDED);

        inputDestination.send(new GenericMessage<>(requestEvent), updateStatusHandlerDestination);
        final Message<byte[]> payload = outputDestination.receive(1000, eventPublisherDestination);

        final OutboxEvent event = objectMapper.readValue(payload.getPayload(), OutboxEvent.class);

        assertThat(event).satisfies(
            actual -> assertThat(actual.getEventId()).isEqualTo(requestEvent.getEventId()),
            actual -> assertThat(actual.getAggregateId()).isEqualTo(requestEvent.getAggregateId()),
            actual -> assertThat(actual.getAggregateType()).isEqualTo(AggregateType.GROUP),
            actual -> assertThat(actual.getEventType()).isEqualTo(EventType.GROUP_STATUS_UPDATED),
            actual -> assertThat(objectMapper.readValue(actual.getEventData(),
                new TypeReference<Map<String, Object>>() {})).isNotNull()
                .isEqualTo(Collections.singletonMap("status",
                    GroupStatus.AUTO_DISBANDED.toString())),
            actual -> assertThat(actual.getEventStatus()).isEqualTo(EventStatus.SUCCESSFUL),
            actual -> assertThat(actual.getWebsocketId()).isEqualTo(requestEvent.getWebsocketId())
        );
    }

    @Test
    @DisplayName("Unsuccessfully fulfills a group status request")
    void disbandsGroupFailure() throws IOException {
        final GroupStatusRequestEvent requestEvent =
            GroupTestUtility.generateGroupStatusRequestEvent(1001L, GroupStatus.AUTO_DISBANDED);

        inputDestination.send(new GenericMessage<>(requestEvent), updateStatusHandlerDestination);
        final Message<byte[]> payload = outputDestination.receive(1000, eventPublisherDestination);

        final OutboxEvent event = objectMapper.readValue(payload.getPayload(), OutboxEvent.class);

        final ErrorData errorData = objectMapper.readValue(event.getEventData(), ErrorData.class);

        assertThat(event).satisfies(
            actual -> assertThat(actual.getEventId()).isEqualTo(requestEvent.getEventId()),
            actual -> assertThat(actual.getAggregateId()).isEqualTo(requestEvent.getAggregateId()),
            actual -> assertThat(actual.getAggregateType()).isEqualTo(AggregateType.GROUP),
            actual -> assertThat(actual.getEventType()).isEqualTo(EventType.GROUP_STATUS_UPDATED),
            actual -> assertThat(actual.getEventStatus()).isEqualTo(EventStatus.FAILED),
            actual -> assertThat(actual.getWebsocketId()).isEqualTo(requestEvent.getWebsocketId())
        );

        assertThat(errorData.error())
            .isEqualTo("Cannot update group status because this group does not exist.");
    }

    @Test
    @DisplayName("Check validations for group create request")
    void checkValidationsForGroupCreateRequest() throws IOException {
        final GroupCreateRequestEvent requestEvent =
            GroupTestUtility.generateGroupCreateRequestEvent(0, 0);

        inputDestination.send(new GenericMessage<>(requestEvent), createHandlerDestination);
        final Message<byte[]> payload = outputDestination.receive(1000, eventPublisherDestination);

        final OutboxEvent event = objectMapper.readValue(payload.getPayload(), OutboxEvent.class);

        final ErrorData errorData = objectMapper.readValue(event.getEventData(), ErrorData.class);

        assertThat(event).satisfies(
            actual -> assertThat(actual.getEventId()).isEqualTo(requestEvent.getEventId()),
            actual -> assertThat(actual.getAggregateId()).isNull(),
            actual -> assertThat(actual.getAggregateType()).isEqualTo(AggregateType.GROUP),
            actual -> assertThat(actual.getEventType()).isEqualTo(EventType.GROUP_CREATED),
            actual -> assertThat(actual.getEventStatus()).isEqualTo(EventStatus.FAILED),
            actual -> assertThat(actual.getWebsocketId()).isEqualTo(requestEvent.getWebsocketId())
        );

        assertThat(errorData.error()).isEqualTo("[Max group size must be a positive value]");
    }
}
