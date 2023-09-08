package com.grouphq.groupservice.group.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

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
import java.time.Instant;
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
            .expectStatus().is2xxSuccessful()
            .expectBody(Member.class).value(memberCreated -> {
                assertThat(memberCreated).isNotNull();
                assertThat(memberCreated.memberStatus())
                    .isEqualTo(MemberStatus.ACTIVE);
            });
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
    }
}
