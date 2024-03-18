package org.grouphq.groupservice.cucumber.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import net.datafaker.Faker;
import org.grouphq.groupservice.config.DataConfig;
import org.grouphq.groupservice.config.SecurityConfig;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.repository.GroupRepository;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.domain.members.Member;
import org.grouphq.groupservice.group.domain.members.repository.MemberRepository;
import org.grouphq.groupservice.group.domain.outbox.OutboxEvent;
import org.grouphq.groupservice.group.domain.outbox.enums.EventStatus;
import org.grouphq.groupservice.group.domain.outbox.enums.EventType;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupJoinRequestEvent;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupLeaveRequestEvent;
import org.grouphq.groupservice.group.event.daos.requestevent.RequestEvent;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest
@Import({DataConfig.class, SecurityConfig.class, TestChannelBinderConfiguration.class})
@Tag("AcceptanceTest")
public class MemberPolicy {
    private static Member member;
    private static Group group;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InputDestination inputDestination;

    @Autowired
    private OutputDestination outputDestination;

    @Value("${spring.cloud.stream.bindings.groupJoinRequests-in-0.destination}")
    private String joinHandlerDestination;

    @Value("${spring.cloud.stream.bindings.groupLeaveRequests-in-0.destination}")
    private String leaveHandlerDestination;

    @Value("${spring.cloud.stream.bindings.processedEvents-out-0.destination}")
    private String eventPublisherDestination;

    private static String userId;

    private static String username;

    @Before
    public void setUp() {
        userId = UUID.randomUUID().toString();
        username = new Faker().name().firstName();
    }

    @Given("there is an active group")
    public void thereIsAnActiveGroup() {
        clearData();

        final Group group = Group.of("Title", "Description", 10,
            GroupStatus.ACTIVE);

        StepVerifier.create(groupRepository.save(group))
            .assertNext(groupToJoin -> MemberPolicy.group = groupToJoin)
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @When("I try to join the group")
    public void iTryToJoinTheGroup() {
        final GroupJoinRequestEvent requestEvent =
            GroupTestUtility.generateGroupJoinRequestEvent(
                userId, username, group.id());

        sendEvent(requestEvent, joinHandlerDestination);
    }

    @Then("I should be a member of the group")
    public void iShouldBeAMemberOfTheGroup() throws IOException {
        final OutboxEvent event = receiveEvent(EventType.MEMBER_JOINED);

        assertThat(event.getEventStatus()).isEqualTo(EventStatus.SUCCESSFUL);
        assertThat(event.getEventData()).isExactlyInstanceOf(Member.class);

        member = (Member) event.getEventData();

        assertThat(event).isNotNull();
        assertThat(event.getEventStatus()).isEqualTo(EventStatus.SUCCESSFUL);
        assertThat(event.getEventType()).isEqualTo(EventType.MEMBER_JOINED);

        StepVerifier.create(Mono.delay(Duration.ofSeconds(1))
            .thenMany(memberRepository.getActiveMembersByGroup(group.id())))
            .expectNextMatches(retrievedMember -> retrievedMember.id().equals(member.id()))
            .verifyComplete();
    }

    @Given("I am in an active group")
    public void iAmInAnActiveGroup() throws IOException {
        clearData();

        final Group groupMemberWillBeIn = Group.of("Title", "Description",
            10, GroupStatus.ACTIVE);

        StepVerifier.create(groupRepository.save(groupMemberWillBeIn))
            .assertNext(groupToJoin -> MemberPolicy.group = groupToJoin)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        final GroupJoinRequestEvent requestEvent =
            GroupTestUtility.generateGroupJoinRequestEvent(userId, username, group.id());

        sendEvent(requestEvent, joinHandlerDestination);
        final OutboxEvent event = receiveEvent(EventType.MEMBER_JOINED);

        assertThat(event.getEventData()).isExactlyInstanceOf(Member.class);
        member = (Member) event.getEventData();
    }

    @When("I try to leave the group")
    public void iTryToLeaveTheGroup()  {
        final GroupLeaveRequestEvent requestEvent =
            GroupTestUtility.generateGroupLeaveRequestEvent(
                member.websocketId().toString(), group.id(), member.id());

        sendEvent(requestEvent, leaveHandlerDestination);
    }

    @Then("I should no longer be an active member of that group")
    public void iShouldNoLongerBeAnActiveMemberOfThatGroup() throws IOException {
        receiveEvent(EventType.MEMBER_LEFT);
        StepVerifier.create(memberRepository.getActiveMembersByGroup(group.id()))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @And("I am a member of the group")
    public void iAmAMemberOfTheGroup() {
        member = Member.of(userId, username, group.id());
        StepVerifier.create(memberRepository.save(member))
            .assertNext(member -> MemberPolicy.member = member)
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Then("I should not be added to the group again")
    public void iShouldNotBeAddedToTheGroupAgain() {
        StepVerifier.create(memberRepository.getActiveMembersByGroup(group.id()))
            .expectNextCount(1)
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Given("there are multiple active groups")
    public void thereAreMultipleActiveGroups() {
        clearData();

        final Group[] groups = {
            GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE),
            GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE),
            GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE)
        };

        StepVerifier.create(
                Mono.when(
                    groupRepository.save(groups[0]),
                    groupRepository.save(groups[1]),
                    groupRepository.save(groups[2])
                )
            )
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @And("I am a member of one group")
    public void iAmAMemberOfOneGroup() {
        StepVerifier.create(groupRepository.findAll())
            .assertNext(group -> MemberPolicy.group = group)
            .thenCancel()
            .verify(Duration.ofSeconds(1));

        member = Member.of(userId, username, group.id());

        StepVerifier.create(memberRepository.save(member))
            .assertNext(member -> MemberPolicy.member = member)
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @When("I try to join a second group")
    public void iTryToJoinASecondGroup() {
        // find a group the user doesn't have a member in and send a join request
        StepVerifier.create(groupRepository.findGroupsByStatus(GroupStatus.ACTIVE))
            .recordWith(ArrayList::new)
            .expectNextCount(3)
            .consumeRecordedWith(groups -> {
                final Group secondGroupToJoin = groups.stream()
                    .filter(group -> !group.id().equals(MemberPolicy.group.id()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No second group found"));

                final GroupJoinRequestEvent requestEvent =
                    GroupTestUtility.generateGroupJoinRequestEvent(
                        userId,
                        username, secondGroupToJoin.id());

                inputDestination.send(new GenericMessage<>(requestEvent), joinHandlerDestination);
                outputDestination.receive(1000, eventPublisherDestination);
            })
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Then("I should not be added to the second group")
    public void iShouldNotBeAddedToTheSecondGroup() {
        final String sql = "SELECT COUNT(*) FROM members WHERE websocket_id = :websocketId "
                     + "AND member_status = 'ACTIVE'";

        final Mono<Map<String, Object>> groupsJoined = databaseClient.sql(sql)
            .bind("websocketId", member.websocketId())
            .fetch()
            .first();

        StepVerifier.create(groupsJoined)
            .assertNext(result -> assertThat(result.get("count")).isEqualTo(1L))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Given("I know the ID of another member in an active group")
    public void iKnowTheIDOfAnotherMemberInAnActiveGroup() {
        clearData();

        final Group group = GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE);

        StepVerifier.create(groupRepository.save(group))
            .assertNext(groupToJoin -> MemberPolicy.group = groupToJoin)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        final Member member = Member.of(UUID.randomUUID(), username, group.id());

        StepVerifier.create(memberRepository.save(member))
            .assertNext(memberToJoin -> MemberPolicy.member = memberToJoin)
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @When("I try to request that member leave the group")
    public void iTryToRequestThatMemberLeaveTheGroup() {
        final GroupLeaveRequestEvent requestEvent =
            GroupTestUtility.generateGroupLeaveRequestEvent(userId,
                group.id(), member.id());

        sendEvent(requestEvent, leaveHandlerDestination);
    }

    @Then("that member should still be in the group")
    public void thatMemberShouldStillBeInTheGroup() throws IOException {
        final OutboxEvent event = receiveEvent(EventType.MEMBER_LEFT);
        assertThat(event).isNotNull();
        assertThat(event.getEventStatus()).isEqualTo(EventStatus.FAILED);
        assertThat(event.getEventType()).isEqualTo(EventType.MEMBER_LEFT);

        StepVerifier.create(memberRepository.getActiveMembersByGroup(group.id()))
            .expectNextMatches(member -> member.id().equals(MemberPolicy.member.id()))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    private void clearData() {
        memberRepository.deleteAll().block();
        groupRepository.deleteAll().block();
    }

    /**
     * Sends an event.
     *
     * @param event RequestEvent to send
     * @param destination Destination to send event to
     * @param <T> Type of RequestEvent
     */
    private <T extends RequestEvent> void sendEvent(T event, String destination) {
        outputDestination.clear();
        inputDestination.send(new GenericMessage<>(event), destination);
    }

    /**
     * Receives an event of the specified type.
     * @param eventTypeToReceive EventType to receive
     * @return OutboxEvent
     * @throws IOException if there is an error deserializing the event
     */
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
}
