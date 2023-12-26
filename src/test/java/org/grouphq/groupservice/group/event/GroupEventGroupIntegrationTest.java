package org.grouphq.groupservice.group.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.domain.groups.repository.GroupRepository;
import org.grouphq.groupservice.group.domain.outbox.ErrorData;
import org.grouphq.groupservice.group.domain.outbox.OutboxEvent;
import org.grouphq.groupservice.group.domain.outbox.enums.AggregateType;
import org.grouphq.groupservice.group.domain.outbox.enums.EventStatus;
import org.grouphq.groupservice.group.domain.outbox.enums.EventType;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupCreateRequestEvent;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupStatusRequestEvent;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
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

    @Value("${spring.cloud.stream.bindings.groupCreateRequests-in-0.destination}")
    private String createHandlerDestination;

    @Value("${spring.cloud.stream.bindings.groupStatusRequests-in-0.destination}")
    private String updateStatusHandlerDestination;

    @Value("${spring.cloud.stream.bindings.processedEvents-out-0.destination}")
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
        // Create request with invalid capacity of 1
        final GroupCreateRequestEvent requestEvent =
            GroupTestUtility.generateGroupCreateRequestEvent(1);

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

        assertThat(errorData.error()).isEqualTo("Cannot create group due to invalid max size value. "
            + "Max size should be at least 2");
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
            actual -> assertThat(actual.getEventType()).isEqualTo(EventType.GROUP_UPDATED),
            actual -> assertThat(objectMapper.readValue(actual.getEventData(), Group.class).status())
                .isEqualTo(GroupStatus.AUTO_DISBANDED),
            actual -> assertThat(actual.getEventStatus()).isEqualTo(EventStatus.SUCCESSFUL),
            actual -> assertThat(actual.getWebsocketId()).isEqualTo(requestEvent.getWebsocketId())
        );
    }

    @Test
    @DisplayName("Sends out a group updated event when status is updated")
    void sendsOutGroupUpdatedEvent() throws IOException {
        final Instant testStartTime = Instant.now();
        final Group group = GroupTestUtility.generateFullGroupDetails(1001L, GroupStatus.ACTIVE);
        StepVerifier.create(groupRepository.save(group))
            .expectNextCount(1)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        final GroupStatusRequestEvent requestEvent =
            GroupTestUtility.generateGroupStatusRequestEvent(1001L, GroupStatus.AUTO_DISBANDED);

        inputDestination.send(new GenericMessage<>(requestEvent), updateStatusHandlerDestination);
        final Message<byte[]> payload = outputDestination.receive(1000, eventPublisherDestination);

        final OutboxEvent event = objectMapper.readValue(payload.getPayload(), OutboxEvent.class);

        assertThat(event).satisfies(
            actual -> assertThat(actual.getEventId()).isEqualTo(requestEvent.getEventId()),
            actual -> assertThat(actual.getAggregateId()).isEqualTo(requestEvent.getAggregateId()),
            actual -> assertThat(actual.getAggregateType()).isEqualTo(AggregateType.GROUP),
            actual -> assertThat(actual.getEventType()).isEqualTo(EventType.GROUP_UPDATED),
            actual -> assertThat(objectMapper.readValue(actual.getEventData(), Group.class).lastModifiedDate())
                .isBetween(testStartTime, Instant.now()),
            actual -> assertThat(actual.getEventStatus()).isEqualTo(EventStatus.SUCCESSFUL),
            actual -> assertThat(actual.getWebsocketId()).isEqualTo(requestEvent.getWebsocketId())
        );
    }

    @Test
    @DisplayName("Unsuccessfully fulfills a group update request for a group that does not exist")
    void disbandsGroupFailure() throws IOException {
        final GroupStatusRequestEvent requestEvent =
            GroupTestUtility.generateGroupStatusRequestEvent(999L, GroupStatus.AUTO_DISBANDED);

        inputDestination.send(new GenericMessage<>(requestEvent), updateStatusHandlerDestination);
        final Message<byte[]> payload = outputDestination.receive(1000, eventPublisherDestination);

        final OutboxEvent event = objectMapper.readValue(payload.getPayload(), OutboxEvent.class);

        final ErrorData errorData = objectMapper.readValue(event.getEventData(), ErrorData.class);

        assertThat(event).satisfies(
            actual -> assertThat(actual.getEventId()).isEqualTo(requestEvent.getEventId()),
            actual -> assertThat(actual.getAggregateId()).isEqualTo(requestEvent.getAggregateId()),
            actual -> assertThat(actual.getAggregateType()).isEqualTo(AggregateType.GROUP),
            actual -> assertThat(actual.getEventType()).isEqualTo(EventType.GROUP_UPDATED),
            actual -> assertThat(actual.getEventStatus()).isEqualTo(EventStatus.FAILED),
            actual -> assertThat(actual.getWebsocketId()).isEqualTo(requestEvent.getWebsocketId())
        );

        assertThat(errorData.error())
            .matches("Cannot fetch group with id: \\d+ because this group does not exist.");
    }

    @Test
    @DisplayName("Check validations for group create request")
    void checkValidationsForGroupCreateRequest() throws IOException {
        final GroupCreateRequestEvent requestEvent =
            GroupTestUtility.generateGroupCreateRequestEvent(0);

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
