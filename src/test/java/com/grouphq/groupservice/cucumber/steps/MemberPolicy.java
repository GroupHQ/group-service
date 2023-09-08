package com.grouphq.groupservice.cucumber.steps;

import com.grouphq.groupservice.config.DataConfig;
import com.grouphq.groupservice.config.SecurityConfig;
import com.grouphq.groupservice.group.domain.groups.Group;
import com.grouphq.groupservice.group.domain.groups.GroupRepository;
import com.grouphq.groupservice.group.domain.groups.GroupStatus;
import com.grouphq.groupservice.group.domain.members.Member;
import com.grouphq.groupservice.group.domain.members.MemberRepository;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import reactor.test.StepVerifier;

@DataR2dbcTest
@Import({DataConfig.class, SecurityConfig.class})
@Tag("AcceptanceTest")
public class MemberPolicy {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MemberRepository memberRepository;

    private Member member;

    private Group group;

    @When("I join a group")
    public void iJoinAGroup() {
        StepVerifier.create(groupRepository.findGroupsByStatus(GroupStatus.ACTIVE))
            .consumeNextWith(groupToJoin -> {
                group = groupToJoin;
                member = Member.of("User", group.id());
            })
            .thenCancel()
            .verify(Duration.ofSeconds(1));

        StepVerifier.create(memberRepository.save(member))
            .consumeNextWith(memberSaved -> member = memberSaved)
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    /* NOTE: This test WILL FAIL if run using the IntelliJ test runner.
     * Doing so will execute this test twice, and have it succeed on the first run but fail the second time
     */
    @Then("I should be a member of that group")
    public void iShouldBeAMemberOfThatGroup() {
        StepVerifier.create(memberRepository.getMembersByGroup(group.id()))
            .expectNextMatches(retrievedMember -> retrievedMember.id().equals(member.id()))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }
}
