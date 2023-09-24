package org.grouphq.groupservice.cucumber.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import org.grouphq.groupservice.config.DataConfig;
import org.grouphq.groupservice.config.SecurityConfig;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupRepository;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.domain.members.Member;
import org.grouphq.groupservice.group.domain.members.MemberRepository;
import org.grouphq.groupservice.group.domain.outbox.OutboxEvent;
import org.grouphq.groupservice.group.domain.outbox.enums.EventStatus;
import org.grouphq.groupservice.group.domain.outbox.enums.EventType;
import org.grouphq.groupservice.group.event.daos.GroupJoinRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupLeaveRequestEvent;
import org.grouphq.groupservice.group.event.daos.RequestEvent;
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
    public static final String USERNAME = "username";
    private static Member member;
    private static Group group;
    private static OutboxEvent event;

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

    @Given("there is an active group")
    public void thereIsAnActiveGroup() {
        clearData();

        final Group group = Group.of("Title", "Description", 10, 5,
            GroupStatus.ACTIVE);

        StepVerifier.create(groupRepository.save(group))
            .consumeNextWith(groupToJoin -> MemberPolicy.group = groupToJoin)
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @When("I try to join the group")
    public void iTryToJoinTheGroup() throws IOException {
        final GroupJoinRequestEvent requestEvent =
            GroupTestUtility.generateGroupJoinRequestEvent(
                member.websocketId().toString(), member.username(), group.id());

        loadEvent(requestEvent, joinHandlerDestination);
        if (event.getEventStatus().equals(EventStatus.SUCCESSFUL)) {
            member = objectMapper.readValue(event.getEventData(), Member.class);
        }
    }

    @Then("I should be a member of the group")
    public void iShouldBeAMemberOfTheGroup() {
        assertThat(event).isNotNull();
        assertThat(event.getEventStatus()).isEqualTo(EventStatus.SUCCESSFUL);
        assertThat(event.getEventType()).isEqualTo(EventType.MEMBER_JOINED);

        StepVerifier.create(Mono.delay(Duration.ofSeconds(1))
            .thenMany(memberRepository.getActiveMembersByGroup(group.id())))
            .expectNextMatches(retrievedMember -> retrievedMember.id().equals(member.id()))
            .verifyComplete();
    }

    @And("the group's current member size should increase by one")
    public void theGroupSCurrentMemberSizeShouldIncreaseByOne() {
        StepVerifier.create(groupRepository.findById(group.id()))
            .expectNextMatches(groupAfterTrigger ->
                groupAfterTrigger.currentGroupSize() == group.currentGroupSize() + 1)
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Given("I am in an active group")
    public void iAmInAnActiveGroup() throws IOException {
        clearData();

        final Group groupMemberWillBeIn = Group.of("Title", "Description",
            10, 5, GroupStatus.ACTIVE);

        StepVerifier.create(groupRepository.save(groupMemberWillBeIn))
            .consumeNextWith(groupToJoin -> MemberPolicy.group = groupToJoin)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        final GroupJoinRequestEvent requestEvent =
            GroupTestUtility.generateGroupJoinRequestEvent(group.id());

        loadEvent(requestEvent, joinHandlerDestination);
        member = objectMapper.readValue(event.getEventData(), Member.class);
    }

    @When("I try to leave the group")
    public void iTryToLeaveTheGroup() throws IOException {
        final GroupLeaveRequestEvent requestEvent =
            GroupTestUtility.generateGroupLeaveRequestEvent(
                member.websocketId().toString(), group.id(), member.id());

        loadEvent(requestEvent, leaveHandlerDestination);
    }

    @Then("I should no longer be an active member of that group")
    public void iShouldNoLongerBeAnActiveMemberOfThatGroup() {
        StepVerifier.create(memberRepository.getActiveMembersByGroup(group.id()))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @And("the group's current member size should decrease by one")
    public void theGroupSCurrentMemberSizeShouldDecreaseByOne() {
        // We haven't updated the group object since we initially joined,
        // so it's currentGroupSize is what the size should be after leaving.
        StepVerifier.create(groupRepository.findById(group.id()))
            .expectNextMatches(groupAfterTrigger ->
                groupAfterTrigger.currentGroupSize() == group.currentGroupSize())
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @And("I am a member of the group")
    public void iAmAMemberOfTheGroup() {
        member = Member.of(UUID.randomUUID(), USERNAME, group.id());
        StepVerifier.create(memberRepository.save(member))
            .consumeNextWith(member -> MemberPolicy.member = member)
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
            .consumeNextWith(group -> MemberPolicy.group = group)
            .thenCancel()
            .verify(Duration.ofSeconds(1));

        member = Member.of(UUID.randomUUID(), USERNAME, group.id());

        StepVerifier.create(memberRepository.save(member))
            .consumeNextWith(member -> MemberPolicy.member = member)
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

                final Member secondMemberByUser =
                    Member.of(member.websocketId(), "username2", secondGroupToJoin.id());

                final GroupJoinRequestEvent requestEvent =
                    GroupTestUtility.generateGroupJoinRequestEvent(
                        member.websocketId().toString(),
                        secondMemberByUser.username(), secondGroupToJoin.id());

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
            .consumeNextWith(result -> assertThat(result.get("count")).isEqualTo(1L))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Given("I know the ID of another member in an active group")
    public void iKnowTheIDOfAnotherMemberInAnActiveGroup() {
        clearData();

        final Group group = GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE);

        StepVerifier.create(groupRepository.save(group))
            .consumeNextWith(groupToJoin -> MemberPolicy.group = groupToJoin)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        final Member member = Member.of(UUID.randomUUID(), USERNAME, group.id());

        StepVerifier.create(memberRepository.save(member))
            .consumeNextWith(memberToJoin -> MemberPolicy.member = memberToJoin)
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @When("I try to request that member leave the group")
    public void iTryToRequestThatMemberLeaveTheGroup() throws IOException {
        final GroupLeaveRequestEvent requestEvent =
            GroupTestUtility.generateGroupLeaveRequestEvent(UUID.randomUUID().toString(),
                group.id(), member.id());

        loadEvent(requestEvent, leaveHandlerDestination);
    }

    @Then("that member should still be in the group")
    public void thatMemberShouldStillBeInTheGroup() {
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
     * Sends an event and saves its response.
     * Note that we clear the output destination to ensure we don't receive old messages.
     *
     * @param event RequestEvent to send
     * @param destination Destination to send event to
     * @param <T> Type of RequestEvent
     */
    private <T extends RequestEvent> void loadEvent(T event, String destination) throws IOException {
        outputDestination.clear();
        inputDestination.send(new GenericMessage<>(event), destination);
        final Message<byte[]> payload = outputDestination.receive(1000, eventPublisherDestination);
        MemberPolicy.event = objectMapper.readValue(payload.getPayload(), OutboxEvent.class);
    }
}
