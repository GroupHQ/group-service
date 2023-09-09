package com.grouphq.groupservice.cucumber.steps;

import com.grouphq.groupservice.config.DataConfig;
import com.grouphq.groupservice.config.SecurityConfig;
import com.grouphq.groupservice.group.domain.groups.Group;
import com.grouphq.groupservice.group.domain.groups.GroupRepository;
import com.grouphq.groupservice.group.domain.groups.GroupStatus;
import com.grouphq.groupservice.group.domain.members.Member;
import com.grouphq.groupservice.group.domain.members.MemberRepository;
import com.grouphq.groupservice.group.web.GroupJoinRequest;
import com.grouphq.groupservice.group.web.GroupLeaveRequest;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

@SpringBootTest
@AutoConfigureWebTestClient
@Import({DataConfig.class, SecurityConfig.class})
@Tag("AcceptanceTest")
public class MemberPolicy {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WebTestClient webTestClient;

    private static Member member;

    private static Group group;

    static final String JOIN_GROUP_ENDPOINT = "/groups/join";

    static final String LEAVE_GROUP_ENDPOINT = "/groups/leave";

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
    public void iJoinTheGroup() {
        final GroupJoinRequest groupJoinRequest = new GroupJoinRequest("User", group.id());

        webTestClient
            .post()
            .uri(JOIN_GROUP_ENDPOINT)
            .bodyValue(groupJoinRequest)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(Member.class)
            .value(member -> MemberPolicy.member = member);
    }

    /* NOTE: This test WILL FAIL if run using the IntelliJ test runner.
     * Doing so will execute this test twice, and have it succeed on the first run but fail the second time
     */
    @Then("I should be a member of the group")
    public void iShouldBeAMemberOfTheGroup() {
        StepVerifier.create(memberRepository.getMembersByGroup(group.id()))
            .expectNextMatches(retrievedMember -> retrievedMember.id().equals(member.id()))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
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
    public void iAmInAnActiveGroup() {
        final Group groupMemberWillBeIn = Group.of("Title", "Description",
            10, 5, GroupStatus.ACTIVE);

        StepVerifier.create(groupRepository.save(groupMemberWillBeIn))
            .consumeNextWith(groupToJoin -> MemberPolicy.group = groupToJoin)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        final GroupJoinRequest groupJoinRequest = new GroupJoinRequest("User", group.id());

        webTestClient
            .post()
            .uri(JOIN_GROUP_ENDPOINT)
            .bodyValue(groupJoinRequest)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(Member.class)
            .value(member -> MemberPolicy.member = member);
    }

    @When("I leave the group")
    public void iLeaveTheGroup() {
        final GroupLeaveRequest groupLeaveRequest = new GroupLeaveRequest(member.id());

        webTestClient
            .post()
            .uri(LEAVE_GROUP_ENDPOINT)
            .bodyValue(groupLeaveRequest)
            .exchange()
            .expectStatus().isNoContent();
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
