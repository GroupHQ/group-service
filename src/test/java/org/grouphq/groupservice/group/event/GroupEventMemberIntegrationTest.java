package org.grouphq.groupservice.group.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.domain.groups.repository.GroupRepository;
import org.grouphq.groupservice.group.domain.members.Member;
import org.grouphq.groupservice.group.domain.members.MemberStatus;
import org.grouphq.groupservice.group.domain.members.repository.MemberRepository;
import org.grouphq.groupservice.group.domain.outbox.ErrorData;
import org.grouphq.groupservice.group.domain.outbox.OutboxEvent;
import org.grouphq.groupservice.group.domain.outbox.enums.AggregateType;
import org.grouphq.groupservice.group.domain.outbox.enums.EventStatus;
import org.grouphq.groupservice.group.domain.outbox.enums.EventType;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupJoinRequestEvent;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupLeaveRequestEvent;
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
class GroupEventMemberIntegrationTest {

    private static final String GROUP = "Group";
    private static final String DESCRIPTION = "Description";

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
    private GroupService groupService;

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

    private OutboxEvent receiveEvent(EventType eventTypeToReceive) throws IOException {
        EventType eventType = null;
        OutboxEvent outboxEvent = null;

        while (eventType != eventTypeToReceive) {
            final Message<byte[]> payload = outputDestination.receive(1000, eventPublisherDestination);

            if (payload == null) {
                fail("Timeout waiting for message at destination: " + eventPublisherDestination);
            }

            assert payload != null;
            outboxEvent = objectMapper.readValue(payload.getPayload(), OutboxEvent.class);
            eventType = outboxEvent.getEventType();
        }

        return outboxEvent;
    }

    @Test
    @DisplayName("Processes a group join request successfully")
    void successfullyJoinsGroup() throws IOException {
        saveGroup(Group.of(GROUP, DESCRIPTION,
            10, GroupStatus.ACTIVE));

        final GroupJoinRequestEvent requestEvent =
            GroupTestUtility.generateGroupJoinRequestEvent(group.id());
        inputDestination.send(new GenericMessage<>(requestEvent), joinHandlerDestination);

        final OutboxEvent event = receiveEvent(EventType.MEMBER_JOINED);

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
            .assertNext(actual ->
                assertMemberEqualsExpectedProperties(
                    actual, requestEvent, MemberStatus.ACTIVE))
            .expectComplete().verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Sends out a group updated event when a member joins a group")
    void sendsOutGroupUpdatedEventWhenMemberJoinsGroup() throws IOException {
        saveGroup(Group.of(GROUP, DESCRIPTION,
            10, GroupStatus.ACTIVE));

        final GroupJoinRequestEvent requestEvent =
            GroupTestUtility.generateGroupJoinRequestEvent(group.id());
        inputDestination.send(new GenericMessage<>(requestEvent), joinHandlerDestination);

        final OutboxEvent groupUpdatedEvent = receiveEvent(EventType.GROUP_UPDATED);

        assertThat(groupUpdatedEvent).satisfies(
            actual -> assertThat(actual.getEventId()).isNotNull(),
            actual -> assertThat(actual.getAggregateId()).isEqualTo(requestEvent.getAggregateId()),
            actual -> assertThat(actual.getAggregateType()).isEqualTo(AggregateType.GROUP),
            actual -> assertThat(actual.getEventType()).isEqualTo(EventType.GROUP_UPDATED),
            actual -> assertThat(actual.getEventStatus()).isEqualTo(EventStatus.SUCCESSFUL),
            actual -> assertThat(actual.getWebsocketId()).isEqualTo(null)
        );

        StepVerifier.create(groupService.findGroupById(group.id()))
            .assertNext(actualGroup -> assertThat(actualGroup.lastModifiedDate()).isAfter(group.lastModifiedDate()))
            .verifyComplete();
    }

    @Test
    @DisplayName("Unsuccessfully joins group because its not active")
    void unsuccessfullyJoinsGroupBecauseItsNotActive() throws IOException {
        saveGroup(Group.of(GROUP, DESCRIPTION,
            10, GroupStatus.AUTO_DISBANDED));

        final GroupJoinRequestEvent requestEvent =
            GroupTestUtility.generateGroupJoinRequestEvent(group.id());

        inputDestination.send(new GenericMessage<>(requestEvent), joinHandlerDestination);

        final OutboxEvent event = receiveEvent(EventType.MEMBER_JOINED);

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

        final OutboxEvent event = receiveEvent(EventType.MEMBER_JOINED);

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
                .isEqualTo("Cannot fetch group with id: 10000 because this group does not exist.")
        );
    }

    @Test
    @DisplayName("Unsuccessfully joins group because its full")
    void unsuccessfullyJoinsGroupBecauseItsFull() throws IOException {
        saveGroup(GroupTestUtility.generateFullGroupDetails(2, GroupStatus.ACTIVE));
        inputDestination.send(
            new GenericMessage<>(GroupTestUtility.generateGroupJoinRequestEvent(group.id())), joinHandlerDestination);
        inputDestination.send(
            new GenericMessage<>(GroupTestUtility.generateGroupJoinRequestEvent(group.id())), joinHandlerDestination);
        receiveEvent(EventType.MEMBER_JOINED);
        receiveEvent(EventType.MEMBER_JOINED);


        final GroupJoinRequestEvent requestEvent =
            GroupTestUtility.generateGroupJoinRequestEvent(group.id());

        inputDestination.send(new GenericMessage<>(requestEvent), joinHandlerDestination);

        final OutboxEvent event = receiveEvent(EventType.MEMBER_JOINED);

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
                .isEqualTo("Cannot join group because this group has reached its maximum size")
        );
    }

    @Test
    @DisplayName("Successfully leaves group")
    void successfullyLeavesGroup() throws IOException {
        saveGroup(Group.of(GROUP, DESCRIPTION, 10, GroupStatus.ACTIVE));

        final GroupJoinRequestEvent joinRequestEvent =
            GroupTestUtility.generateGroupJoinRequestEvent(group.id());

        inputDestination.send(new GenericMessage<>(joinRequestEvent), joinHandlerDestination);

        OutboxEvent event = receiveEvent(EventType.MEMBER_JOINED);
        final Map<String, Object> memberData = objectMapper.readValue(event.getEventData(),
            new TypeReference<>() {});
        final Integer memberIdInt = (Integer) memberData.get("id");
        final Long memberId = memberIdInt.longValue();

        final GroupLeaveRequestEvent leaveRequestEvent =
            GroupTestUtility.generateGroupLeaveRequestEvent(
                joinRequestEvent.getWebsocketId(), group.id(), memberId);

        inputDestination.send(new GenericMessage<>(leaveRequestEvent), leaveHandlerDestination);

        event = receiveEvent(EventType.MEMBER_LEFT);

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

        // Verify member was removed from the group
        StepVerifier.create(memberRepository.findById(memberId))
            .expectNextMatches(member -> member.memberStatus().equals(MemberStatus.LEFT))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Sends out a group updated event when a member leaves a group")
    void sendsOutGroupUpdatedEventWhenMemberLeavesGroup() throws IOException {
        saveGroup(Group.of(GROUP, DESCRIPTION, 10, GroupStatus.ACTIVE));

        final GroupJoinRequestEvent joinRequestEvent =
            GroupTestUtility.generateGroupJoinRequestEvent(group.id());

        inputDestination.send(new GenericMessage<>(joinRequestEvent), joinHandlerDestination);

        final OutboxEvent event = receiveEvent(EventType.MEMBER_JOINED);
        final Map<String, Object> memberData = objectMapper.readValue(event.getEventData(),
            new TypeReference<>() {
            });
        final Integer memberIdInt = (Integer) memberData.get("id");
        final Long memberId = memberIdInt.longValue();

        final GroupLeaveRequestEvent leaveRequestEvent =
            GroupTestUtility.generateGroupLeaveRequestEvent(
                joinRequestEvent.getWebsocketId(), group.id(), memberId);

        inputDestination.send(new GenericMessage<>(leaveRequestEvent), leaveHandlerDestination);

        final OutboxEvent groupUpdatedEvent = receiveEvent(EventType.GROUP_UPDATED);

        assertThat(groupUpdatedEvent).satisfies(
            actual -> assertThat(actual.getEventId()).isNotNull(),
            actual -> assertThat(actual.getAggregateId()).isEqualTo(leaveRequestEvent.getAggregateId()),
            actual -> assertThat(actual.getAggregateType()).isEqualTo(AggregateType.GROUP),
            actual -> assertThat(actual.getEventType()).isEqualTo(EventType.GROUP_UPDATED),
            actual -> assertThat(actual.getEventStatus()).isEqualTo(EventStatus.SUCCESSFUL),
            actual -> assertThat(actual.getWebsocketId()).isEqualTo(null)
        );

        StepVerifier.create(groupService.findGroupById(group.id()))
            .assertNext(actualGroup -> assertThat(actualGroup.lastModifiedDate()).isAfter(group.lastModifiedDate()))
            .verifyComplete();
    }

    @Test
    @DisplayName("Unsuccessfully leaves group because group does not exist")
    void unsuccessfullyLeavesGroup() throws IOException {
        final GroupLeaveRequestEvent leaveRequestEvent =
            GroupTestUtility.generateGroupLeaveRequestEvent(10_000L, 10_000L);

        inputDestination.send(new GenericMessage<>(leaveRequestEvent), leaveHandlerDestination);
        final OutboxEvent event = receiveEvent(EventType.MEMBER_LEFT);

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
                .isEqualTo("Cannot remove member because either the member does not exist "
                    + "or you do not have appropriate authorization. "
                    + "Make sure you are using the correct member ID and websocket ID."));
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
            .assertNext(savedGroup -> this.group = savedGroup)
            .expectComplete().verify(Duration.ofSeconds(1));
    }
}
