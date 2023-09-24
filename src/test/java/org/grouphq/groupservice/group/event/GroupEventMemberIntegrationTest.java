package org.grouphq.groupservice.group.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupRepository;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.domain.members.Member;
import org.grouphq.groupservice.group.domain.members.MemberRepository;
import org.grouphq.groupservice.group.domain.members.MemberStatus;
import org.grouphq.groupservice.group.domain.outbox.ErrorData;
import org.grouphq.groupservice.group.domain.outbox.OutboxEvent;
import org.grouphq.groupservice.group.domain.outbox.enums.AggregateType;
import org.grouphq.groupservice.group.domain.outbox.enums.EventStatus;
import org.grouphq.groupservice.group.domain.outbox.enums.EventType;
import org.grouphq.groupservice.group.event.daos.GroupJoinRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupLeaveRequestEvent;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
import java.io.IOException;
import java.time.Duration;
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
class GroupEventMemberIntegrationTest {

    /**
     * Both InputDestination and OutputDestination are provided by the
     * TestChannelBinderConfiguration class, which is imported above.
     * IntelliJ or other IDEs may complain with the following error:
     * "Could not autowire. No beans of 'InputDestination' type found."
     * This is a false positive. The project will still compile and run as expected.
     */
    @Autowired
    private InputDestination inputDestination;

    @Autowired
    private OutputDestination outputDestination;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ObjectMapper objectMapper;
    private Group group;

    @Value("${spring.cloud.stream.bindings.groupJoinRequests-in-0.destination}")
    private String joinHandlerDestination;

    @Value("${spring.cloud.stream.bindings.groupLeaveRequests-in-0.destination}")
    private String leaveHandlerDestination;

    @Value("${spring.cloud.stream.bindings.processedEvents-out-0.destination}")
    private String eventPublisherDestination;

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.4"));

    @DynamicPropertySource
    private static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", GroupEventMemberIntegrationTest::r2dbcUrl);
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
    @DisplayName("Verify destination configurations exist")
    void verifyDestinationConfigurationsExist() {
        assertThat(joinHandlerDestination).isNotNull();
        assertThat(eventPublisherDestination).isNotNull();
    }

    @Test
    @DisplayName("Processes a group join request successfully")
    void successfullyJoinsGroup() throws IOException {
        saveGroup(Group.of("Group", "Description",
            10, 5, GroupStatus.ACTIVE));

        final GroupJoinRequestEvent requestEvent =
            GroupTestUtility.generateGroupJoinRequestEvent(group.id());
        inputDestination.send(new GenericMessage<>(requestEvent), joinHandlerDestination);

        final Message<byte[]> payload = outputDestination.receive(1000, eventPublisherDestination);

        final OutboxEvent event = objectMapper.readValue(payload.getPayload(), OutboxEvent.class);

        assertThat(event).satisfies(
            actual -> assertThat(actual.getEventId()).isEqualTo(requestEvent.getEventId()),
            actual -> assertThat(actual.getAggregateId()).isEqualTo(requestEvent.getAggregateId()),
            actual -> assertThat(actual.getAggregateType()).isEqualTo(AggregateType.GROUP),
            actual -> assertThat(actual.getEventType()).isEqualTo(EventType.MEMBER_JOINED),
            actual -> assertThat(actual.getEventStatus()).isEqualTo(EventStatus.SUCCESSFUL),
            actual -> assertThat(actual.getWebsocketId()).isEqualTo(requestEvent.getWebsocketId())
        );

        // Check that member has the appropriate identifying values
        final Member member = objectMapper.readValue(event.getEventData(), Member.class);
        assertMemberEqualsExpectedProperties(member, requestEvent, MemberStatus.ACTIVE);

        // Verify this member was saved to the database
        StepVerifier.create(memberRepository.findById(member.id()))
            .consumeNextWith(actual ->
                assertMemberEqualsExpectedProperties(
                    actual, requestEvent, MemberStatus.ACTIVE))
            .expectComplete().verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Unsuccessfully joins group because its not active")
    void unsuccessfullyJoinsGroupBecauseItsNotActive() throws IOException {
        saveGroup(Group.of("Group", "Description",
            10, 5, GroupStatus.AUTO_DISBANDED));

        final GroupJoinRequestEvent requestEvent =
            GroupTestUtility.generateGroupJoinRequestEvent(group.id());

        inputDestination.send(new GenericMessage<>(requestEvent), joinHandlerDestination);

        final Message<byte[]> payload = outputDestination.receive(1000, eventPublisherDestination);

        final OutboxEvent event = objectMapper.readValue(payload.getPayload(), OutboxEvent.class);

        assertThat(event).satisfies(
            actual -> assertThat(actual.getEventId()).isEqualTo(requestEvent.getEventId()),
            actual -> assertThat(actual.getAggregateId()).isEqualTo(requestEvent.getAggregateId()),
            actual -> assertThat(actual.getAggregateType()).isEqualTo(AggregateType.GROUP),
            actual -> assertThat(actual.getEventType()).isEqualTo(EventType.MEMBER_JOINED),
            actual -> assertThat(actual.getEventStatus()).isEqualTo(EventStatus.FAILED),
            actual -> assertThat(actual.getWebsocketId()).isEqualTo(requestEvent.getWebsocketId())
        );

        // Check that event data maps to an error
        final ErrorData error = objectMapper.readValue(event.getEventData(), ErrorData.class);

        assertThat(error).satisfies(
            actual -> assertThat(actual.error())
                .isEqualTo("Cannot save member because this group is not active.")
        );
    }

    @Test
    @DisplayName("Unsuccessfully joins group because it does not exist")
    void unsuccessfullyJoinsGroupBecauseItDoesNotExist() throws IOException {
        final GroupJoinRequestEvent requestEvent =
            GroupTestUtility.generateGroupJoinRequestEvent(10_000L);

        inputDestination.send(new GenericMessage<>(requestEvent), joinHandlerDestination);

        final Message<byte[]> payload = outputDestination.receive(1_000, eventPublisherDestination);

        final OutboxEvent event = objectMapper.readValue(payload.getPayload(), OutboxEvent.class);

        assertThat(event).satisfies(
            actual -> assertThat(actual.getEventId()).isEqualTo(requestEvent.getEventId()),
            actual -> assertThat(actual.getAggregateId()).isEqualTo(requestEvent.getAggregateId()),
            actual -> assertThat(actual.getAggregateType()).isEqualTo(AggregateType.GROUP),
            actual -> assertThat(actual.getEventType()).isEqualTo(EventType.MEMBER_JOINED),
            actual -> assertThat(actual.getEventStatus()).isEqualTo(EventStatus.FAILED),
            actual -> assertThat(actual.getWebsocketId()).isEqualTo(requestEvent.getWebsocketId())
        );

        // Check that event data maps to an error
        final ErrorData error = objectMapper.readValue(event.getEventData(), ErrorData.class);

        assertThat(error).satisfies(
            actual -> assertThat(actual.error())
                .isEqualTo("Cannot save member because this group does not exist.")
        );
    }

    @Test
    @DisplayName("Unsuccessfully joins group because its full")
    void unsuccessfullyJoinsGroupBecauseItsFull() throws IOException {
        saveGroup(GroupTestUtility.generateFullGroupDetails(10, 10, GroupStatus.ACTIVE));

        final GroupJoinRequestEvent requestEvent =
            GroupTestUtility.generateGroupJoinRequestEvent(group.id());

        inputDestination.send(new GenericMessage<>(requestEvent), joinHandlerDestination);

        final Message<byte[]> payload = outputDestination.receive(1000, eventPublisherDestination);

        final OutboxEvent event = objectMapper.readValue(payload.getPayload(), OutboxEvent.class);

        assertThat(event).satisfies(
            actual -> assertThat(actual.getEventId()).isEqualTo(requestEvent.getEventId()),
            actual -> assertThat(actual.getAggregateId()).isEqualTo(requestEvent.getAggregateId()),
            actual -> assertThat(actual.getAggregateType()).isEqualTo(AggregateType.GROUP),
            actual -> assertThat(actual.getEventType()).isEqualTo(EventType.MEMBER_JOINED),
            actual -> assertThat(actual.getEventStatus()).isEqualTo(EventStatus.FAILED),
            actual -> assertThat(actual.getWebsocketId()).isEqualTo(requestEvent.getWebsocketId())
        );

        // Check that event data maps to an error
        final ErrorData error = objectMapper.readValue(event.getEventData(), ErrorData.class);

        assertThat(error).satisfies(
            actual -> assertThat(actual.error())
                .isEqualTo("Cannot save member because this group is full.")
        );
    }

    @Test
    @DisplayName("Successfully leaves group")
    void successfullyLeavesGroup() throws IOException {
        saveGroup(Group.of("Group", "Description", 10, 5, GroupStatus.ACTIVE));

        final GroupJoinRequestEvent joinRequestEvent =
            GroupTestUtility.generateGroupJoinRequestEvent(group.id());

        inputDestination.send(new GenericMessage<>(joinRequestEvent), joinHandlerDestination);
        Message<byte[]> payload = outputDestination.receive(1000, eventPublisherDestination);

        OutboxEvent event = objectMapper.readValue(payload.getPayload(), OutboxEvent.class);
        final Map<String, Object> memberData = objectMapper.readValue(event.getEventData(),
            new TypeReference<>() {});
        final Integer memberIdInt = (Integer) memberData.get("id");
        final Long memberId = memberIdInt.longValue();

        final GroupLeaveRequestEvent leaveRequestEvent =
            GroupTestUtility.generateGroupLeaveRequestEvent(group.id(), memberId);

        inputDestination.send(new GenericMessage<>(leaveRequestEvent), leaveHandlerDestination);
        payload = outputDestination.receive(1000, eventPublisherDestination);

        event = objectMapper.readValue(payload.getPayload(), OutboxEvent.class);

        assertThat(event).satisfies(
            actual -> assertThat(actual.getEventId())
                .isEqualTo(leaveRequestEvent.getEventId()),
            actual -> assertThat(actual.getAggregateId())
                .isEqualTo(leaveRequestEvent.getAggregateId()),
            actual -> assertThat(actual.getAggregateType())
                .isEqualTo(AggregateType.GROUP),
            actual -> assertThat(actual.getEventType())
                .isEqualTo(EventType.MEMBER_LEFT),
            actual -> assertThat(actual.getEventStatus())
                .isEqualTo(EventStatus.SUCCESSFUL),
            actual -> assertThat(actual.getWebsocketId())
                .isEqualTo(leaveRequestEvent.getWebsocketId())
        );

        // Verify member was removed from group
        StepVerifier.create(memberRepository.findById(memberId))
            .expectNextMatches(member -> member.memberStatus().equals(MemberStatus.LEFT))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Unsuccessfully leaves group because group does not exist")
    void unsuccessfullyLeavesGroup() throws IOException {
        final GroupLeaveRequestEvent leaveRequestEvent =
            GroupTestUtility.generateGroupLeaveRequestEvent(10_000L, 10_000L);

        inputDestination.send(new GenericMessage<>(leaveRequestEvent), leaveHandlerDestination);
        final Message<byte[]> payload = outputDestination.receive(1_000, eventPublisherDestination);

        final OutboxEvent event = objectMapper.readValue(payload.getPayload(), OutboxEvent.class);

        assertThat(event).satisfies(
            actual -> assertThat(actual.getEventId())
                .isEqualTo(leaveRequestEvent.getEventId()),
            actual -> assertThat(actual.getAggregateId())
                .isEqualTo(leaveRequestEvent.getAggregateId()),
            actual -> assertThat(actual.getAggregateType())
                .isEqualTo(AggregateType.GROUP),
            actual -> assertThat(actual.getEventType())
                .isEqualTo(EventType.MEMBER_LEFT),
            actual -> assertThat(actual.getEventStatus())
                .isEqualTo(EventStatus.FAILED),
            actual -> assertThat(actual.getWebsocketId())
                .isEqualTo(leaveRequestEvent.getWebsocketId())
        );

        // Check that event data maps to an error
        final ErrorData error = objectMapper.readValue(event.getEventData(), ErrorData.class);

        assertThat(error).satisfies(
            actual -> assertThat(actual.error())
                .isEqualTo("Cannot remove member because this group does not exist."));
    }

    private void assertMemberEqualsExpectedProperties(
        Member member,
        GroupJoinRequestEvent joinRequestEvent,
        MemberStatus memberStatus) {

        assertThat(member).satisfies(
            actual -> assertThat(actual.username()).isEqualTo(joinRequestEvent.getUsername()),
            actual -> assertThat(actual.groupId()).isEqualTo(joinRequestEvent.getAggregateId()),
            actual -> assertThat(actual.memberStatus()).isEqualTo(memberStatus));
    }

    private void saveGroup(Group group) {
        StepVerifier.create(groupRepository.save(group))
            .consumeNextWith(savedGroup -> this.group = savedGroup)
            .expectComplete().verify(Duration.ofSeconds(1));
    }
}
