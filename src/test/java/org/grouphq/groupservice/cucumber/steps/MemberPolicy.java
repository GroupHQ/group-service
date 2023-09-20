package org.grouphq.groupservice.cucumber.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.grouphq.groupservice.config.DataConfig;
import org.grouphq.groupservice.config.SecurityConfig;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupRepository;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.domain.members.Member;
import org.grouphq.groupservice.group.domain.members.MemberRepository;
import org.grouphq.groupservice.group.domain.outbox.OutboxEvent;
import org.grouphq.groupservice.group.event.daos.GroupJoinRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupLeaveRequestEvent;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.time.Duration;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest
@Import({DataConfig.class, SecurityConfig.class, TestChannelBinderConfiguration.class})
@Tag("AcceptanceTest")
public class MemberPolicy {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InputDestination inputDestination;

    @Autowired
    private OutputDestination outputDestination;

    @Value("${spring.cloud.stream.bindings.handleGroupJoinRequests-in-0.destination}")
    private String joinHandlerDestination;

    @Value("${spring.cloud.stream.bindings.handleGroupLeaveRequests-in-0.destination}")
    private String leaveHandlerDestination;

    @Value("${spring.cloud.stream.bindings.forwardProcessedEvents-out-0.destination}")
    private String eventPublisherDestination;

    private static Member member;

    private static Group group;

    @Given("there is an active group")
    public void thereIsAnActiveGroup() {
        final Group group = Group.of("Title", "Description", 10, 5,
            GroupStatus.ACTIVE);

        StepVerifier.create(groupRepository.save(group))
            .consumeNextWith(groupToJoin -> MemberPolicy.group = groupToJoin)
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @When("I join the group")
    public void iJoinTheGroup() throws IOException {
        final GroupJoinRequestEvent requestEvent =
            GroupTestUtility.generateGroupJoinRequestEvent(group.id());

        inputDestination.send(new GenericMessage<>(requestEvent), joinHandlerDestination);

        final Message<byte[]> payload = outputDestination.receive(1000, eventPublisherDestination);
        final OutboxEvent event = objectMapper.readValue(payload.getPayload(), OutboxEvent.class);
        member = objectMapper.readValue(event.getEventData(), Member.class);
    }

    @Then("I should be a member of the group")
    public void iShouldBeAMemberOfTheGroup() {
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
        final Group groupMemberWillBeIn = Group.of("Title", "Description",
            10, 5, GroupStatus.ACTIVE);

        StepVerifier.create(groupRepository.save(groupMemberWillBeIn))
            .consumeNextWith(groupToJoin -> MemberPolicy.group = groupToJoin)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        final GroupJoinRequestEvent requestEvent =
            GroupTestUtility.generateGroupJoinRequestEvent(group.id());

        inputDestination.send(new GenericMessage<>(requestEvent), joinHandlerDestination);

        final Message<byte[]> payload = outputDestination.receive(1000, eventPublisherDestination);
        final OutboxEvent event = objectMapper.readValue(payload.getPayload(), OutboxEvent.class);
        member = objectMapper.readValue(event.getEventData(), Member.class);
    }

    @When("I leave the group")
    public void iLeaveTheGroup() {
        final GroupLeaveRequestEvent requestEvent =
            GroupTestUtility.generateGroupLeaveRequestEvent(group.id(), member.id());

        inputDestination.send(new GenericMessage<>(requestEvent), leaveHandlerDestination);
        outputDestination.receive(1000, eventPublisherDestination);
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
}
