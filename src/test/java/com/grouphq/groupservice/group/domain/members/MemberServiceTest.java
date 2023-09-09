package com.grouphq.groupservice.group.domain.members;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.grouphq.groupservice.group.domain.groups.Group;
import com.grouphq.groupservice.group.domain.groups.GroupRepository;
import com.grouphq.groupservice.group.domain.members.exceptions.GroupIsFullException;
import com.grouphq.groupservice.group.domain.members.exceptions.MemberNotActiveException;
import com.grouphq.groupservice.group.testutility.GroupTestUtility;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Tag("UnitTest")
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    static final String USERNAME = "User";

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private MemberService memberService;

    private Group group;

    @BeforeEach
    void setUpGroup() {
        group = GroupTestUtility.generateFullGroupDetails();
    }

    @Test
    @DisplayName("Gets members from a group")
    void viewGroupMembers() {
        final Member[] members = {
            Member.of("User1", group.id()),
            Member.of("User2", group.id()),
            Member.of("User3", group.id())
        };

        given(memberRepository.getActiveMembersByGroup(group.id())).willReturn(Flux.just(members));

        StepVerifier.create(memberService.getActiveMembers(group.id()))
            .expectNext(members[0], members[1], members[2])
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(memberRepository, times(1)).getActiveMembersByGroup(group.id());
    }

    @Test
    @DisplayName("Creates and adds a member to an active group")
    void joinGroup() {
        given(groupRepository.findById(group.id())).willReturn(Mono.just(group));

        final Member member = Member.of(USERNAME, group.id());
        given(memberRepository.save(member))
            .willReturn(Mono.just(member));

        StepVerifier.create(memberService.joinGroup(USERNAME, group.id()))
            .expectNextMatches(savedMember -> savedMember.groupId().equals(member.groupId()))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(groupRepository, times(1)).findById(group.id());
        verify(memberRepository, times(1)).save(member);
    }

    @Test
    @DisplayName("Removes a member from their group")
    void removesMemberFromTheirGroup() {
        final Long memberId = 1234L;

        given(memberRepository.removeMemberFromGroup(memberId)).willReturn(Mono.empty());

        StepVerifier.create(memberService.removeMember(memberId))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(memberRepository, times(1)).removeMemberFromGroup(memberId);
    }

    @Test
    @DisplayName("Does not create a member if group to join is not active")
    void disallowNonActiveGroupJoining() {
        given(groupRepository.findById(group.id())).willReturn(Mono.just(group));

        given(memberRepository.save(any(Member.class))).willThrow(
            new DataAccessResourceFailureException(
                "Cannot update member because member status is not ACTIVE")
        );

        StepVerifier.create(memberService.joinGroup(USERNAME, group.id()))
            .expectErrorMatches(throwable -> throwable instanceof MemberNotActiveException)
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Does not create a member if group to join is full")
    void disallowFullGroupJoining() {
        given(groupRepository.findById(group.id())).willReturn(Mono.just(group));

        given(memberRepository.save(any(Member.class))).willThrow(
            new DataAccessResourceFailureException(
                "Cannot save member with group because the group is full")
        );

        StepVerifier.create(memberService.joinGroup(USERNAME, group.id()))
            .expectErrorMatches(throwable -> throwable instanceof GroupIsFullException)
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Does not update a member if member is not active")
    void disallowMemberUpdateWhenMemberIsNonActive() {
        given(groupRepository.findById(group.id())).willReturn(Mono.just(group));

        given(memberRepository.save(any(Member.class))).willThrow(
            new DataAccessResourceFailureException(
                "Cannot update member because member status is not ACTIVE")
        );

        StepVerifier.create(memberService.joinGroup(USERNAME, group.id()))
            .expectErrorMatches(throwable -> throwable instanceof MemberNotActiveException)
            .verify(Duration.ofSeconds(1));
    }

}
