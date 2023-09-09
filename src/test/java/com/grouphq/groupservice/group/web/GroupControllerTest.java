package com.grouphq.groupservice.group.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.grouphq.groupservice.config.SecurityConfig;
import com.grouphq.groupservice.group.domain.groups.Group;
import com.grouphq.groupservice.group.domain.groups.GroupService;
import com.grouphq.groupservice.group.domain.groups.GroupStatus;
import com.grouphq.groupservice.group.domain.members.Member;
import com.grouphq.groupservice.group.domain.members.MemberService;
import com.grouphq.groupservice.group.domain.members.MemberStatus;
import com.grouphq.groupservice.group.domain.members.exceptions.GroupIsFullException;
import com.grouphq.groupservice.group.domain.members.exceptions.GroupNotActiveException;
import com.grouphq.groupservice.group.domain.members.exceptions.InternalServerError;
import com.grouphq.groupservice.group.testutility.GroupTestUtility;
import com.grouphq.groupservice.group.web.objects.GroupJoinRequest;
import com.grouphq.groupservice.group.web.objects.GroupLeaveRequest;
import com.grouphq.groupservice.group.web.objects.egress.PublicMember;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest(GroupController.class)
@Import(SecurityConfig.class)
@Tag("IntegrationTest")
class GroupControllerTest {

    static final String JOIN_GROUP_ENDPOINT = "/groups/join";
    static final String LEAVE_GROUP_ENDPOINT = "/groups/leave";

    @Autowired
    WebTestClient webTestClient;

    @MockBean
    GroupService groupService;

    @MockBean
    MemberService memberService;

    @Test
    @DisplayName("When there are active groups, then return a list of active groups")
    void returnActiveGroups() {
        final Group[] testGroups = {
            GroupTestUtility.generateFullGroupDetails(),
            GroupTestUtility.generateFullGroupDetails()
        };

        given(groupService.getGroups()).willReturn(Flux.just(testGroups));

        webTestClient
            .get()
            .uri("/groups")
            .exchange()
            .expectStatus().is2xxSuccessful()
            .expectBodyList(Group.class).value(groups -> {
                assertThat(groups).isNotEmpty();
                assertThat(groups).allMatch(group ->
                    group.status().equals(GroupStatus.ACTIVE),
                    "All groups received should be active");
            });

        verify(groupService, times(1)).getGroups();
    }

    @Test
    @DisplayName("Allow users to retrieve active group members as public")
    void retrieveActiveGroupMembersAsPublic() {
        final Member[] members = {
            GroupTestUtility.generateFullMemberDetails(),
            GroupTestUtility.generateFullMemberDetails(),
            GroupTestUtility.generateFullMemberDetails()
        };

        given(memberService.getActiveMembers(1234L))
            .willReturn(Flux.just(members));

        final List<PublicMember> publicMembers = List.of(
            new PublicMember(members[0].username(), members[0].groupId(),
                members[0].memberStatus(), members[0].joinedDate(), members[0].exitedDate()),
            new PublicMember(members[1].username(), members[1].groupId(),
                members[1].memberStatus(), members[1].joinedDate(), members[1].exitedDate()),
            new PublicMember(members[2].username(), members[2].groupId(),
                members[2].memberStatus(), members[2].joinedDate(), members[2].exitedDate())
        );

        webTestClient
            .get()
            .uri("/groups/1234/members")
            .exchange()
            .expectStatus().is2xxSuccessful()
            .expectBodyList(PublicMember.class).value(retrievedMembers ->
                assertThat(retrievedMembers)
                    .containsExactlyInAnyOrderElementsOf(publicMembers));

        verify(memberService, times(1))
            .getActiveMembers(1234L);
    }

    @Test
    @DisplayName("Allow users to join active groups")
    void joinGroup() {
        final Member member = new Member(
            1234L, "User 1", 1234L, MemberStatus.ACTIVE,
            Instant.now(), null, Instant.now(), Instant.now(),
            "system", "system", 0
        );

        final GroupJoinRequest groupJoinRequest = new GroupJoinRequest("User 1", 1234L);

        given(memberService.joinGroup(
            groupJoinRequest.username(), groupJoinRequest.groupId()))
            .willReturn(Mono.just(member));

        webTestClient
            .post()
            .uri(JOIN_GROUP_ENDPOINT)
            .bodyValue(groupJoinRequest)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(Member.class).value(memberCreated -> {
                assertThat(memberCreated).isNotNull();
                assertThat(memberCreated.memberStatus())
                    .isEqualTo(MemberStatus.ACTIVE);
            });

        verify(memberService, times(1)).joinGroup(
            groupJoinRequest.username(), groupJoinRequest.groupId());
    }

    @Test
    @DisplayName("Allow users to leave groups")
    void leaveGroup() {
        final GroupLeaveRequest groupLeaveRequest = new GroupLeaveRequest(1234L);

        given(memberService.removeMember(groupLeaveRequest.memberId())).willReturn(Mono.empty());

        webTestClient
            .post()
            .uri(LEAVE_GROUP_ENDPOINT)
            .bodyValue(groupLeaveRequest)
            .exchange()
            .expectStatus().isNoContent();

        verify(memberService, times(1)).removeMember(groupLeaveRequest.memberId());
    }

    @Test
    @DisplayName("Handle GroupNotActiveException and send back corresponding response")
    void receiveGroupNotActiveExceptionWhenTryingToJoinNonActiveGroup() {
        final GroupJoinRequest groupJoinRequest = new GroupJoinRequest("User 2", 1234L);

        given(memberService.joinGroup(
            groupJoinRequest.username(), groupJoinRequest.groupId()))
            .willReturn(Mono.error(new GroupNotActiveException("Cannot save member")));

        webTestClient
            .post()
            .uri(JOIN_GROUP_ENDPOINT)
            .bodyValue(groupJoinRequest)
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.GONE)
            .expectBody(String.class).value(message ->
                    assertThat(message).isEqualTo(
                        "Cannot save member because this group is not active"));

        verify(memberService, times(1)).joinGroup(
            groupJoinRequest.username(), groupJoinRequest.groupId());
    }

    @Test
    @DisplayName("Handle GroupIsFullException and send back corresponding response")
    void receiveGroupIsFullExceptionWhenTryingToJoinFullGroup() {
        final GroupJoinRequest groupJoinRequest = new GroupJoinRequest("User 3", 1234L);

        given(memberService.joinGroup(
            groupJoinRequest.username(), groupJoinRequest.groupId()))
            .willReturn(Mono.error(new GroupIsFullException("Cannot save member")));

        webTestClient
            .post()
            .uri(JOIN_GROUP_ENDPOINT)
            .bodyValue(groupJoinRequest)
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectBody(String.class).value(message ->
                assertThat(message).isEqualTo(
                    "Cannot save member because this group is full"));

        verify(memberService, times(1)).joinGroup(
            groupJoinRequest.username(), groupJoinRequest.groupId());
    }

    @Test
    @DisplayName("Handle InternalServerError and send back corresponding response")
    void receiveInternalServerErrorWhenForUnrecognizedException() {
        final GroupJoinRequest groupJoinRequest = new GroupJoinRequest("User 4", 1234L);

        given(memberService.joinGroup(
            groupJoinRequest.username(), groupJoinRequest.groupId()))
            .willReturn(Mono.error(new InternalServerError()));

        webTestClient
            .post()
            .uri(JOIN_GROUP_ENDPOINT)
            .bodyValue(groupJoinRequest)
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
            .expectBody(String.class).value(message ->
                assertThat(message).isEqualTo(
                    """
                    The server has encountered an unexpected error.
                    Rest assured, this will be investigated.
                    """));

        verify(memberService, times(1)).joinGroup(
            groupJoinRequest.username(), groupJoinRequest.groupId());
    }
}
