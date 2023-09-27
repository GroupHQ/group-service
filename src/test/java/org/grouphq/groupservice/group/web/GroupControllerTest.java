package org.grouphq.groupservice.group.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.grouphq.groupservice.config.SecurityConfig;
import org.grouphq.groupservice.group.domain.exceptions.InternalServerError;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.domain.members.Member;
import org.grouphq.groupservice.group.domain.members.MemberService;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
import org.grouphq.groupservice.group.web.objects.egress.PublicMember;
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

@WebFluxTest(GroupController.class)
@Import(SecurityConfig.class)
@Tag("IntegrationTest")
class GroupControllerTest {

    private static final String GROUPS_ENDPOINT = "/groups";

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private GroupService groupService;

    @MockBean
    private MemberService memberService;

    @Test
    @DisplayName("When there are active groups, then return a list of active groups")
    void returnActiveGroups() {
        final Group[] testGroups = {
            GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE),
            GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE)
        };

        given(groupService.getGroups()).willReturn(Flux.just(testGroups));

        webTestClient
            .get()
            .uri(GROUPS_ENDPOINT)
            .exchange()
            .expectStatus().is2xxSuccessful()
            .expectBodyList(Group.class).value(groups -> {
                assertThat(groups).isNotEmpty();
                assertThat(groups).allMatch(group ->
                    group.status().equals(GroupStatus.ACTIVE),
                    "All groups received should be active");
            });

        verify(groupService).getGroups();
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
            new PublicMember(members[0].id(), members[0].username(), members[0].groupId(),
                members[0].memberStatus(), members[0].joinedDate(), members[0].exitedDate()),
            new PublicMember(members[1].id(), members[1].username(), members[1].groupId(),
                members[1].memberStatus(), members[1].joinedDate(), members[1].exitedDate()),
            new PublicMember(members[2].id(), members[2].username(), members[2].groupId(),
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

        verify(memberService).getActiveMembers(1234L);
    }

    @Test
    @DisplayName("Handle InternalServerError and send back corresponding response")
    void receiveInternalServerErrorWhenForUnrecognizedException() {
        given(groupService.getGroups()).willReturn(Flux.error(new InternalServerError()));

        webTestClient
            .get()
            .uri(GROUPS_ENDPOINT)
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
            .expectBody(String.class).value(message ->
                assertThat(message).isEqualTo(
                    """
                    The server has encountered an unexpected error.
                    Rest assured, this will be investigated.
                    """));

        verify(groupService).getGroups();
    }
}
