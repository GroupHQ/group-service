package org.grouphq.groupservice.group.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.grouphq.groupservice.config.SecurityConfig;
import org.grouphq.groupservice.group.domain.exceptions.InternalServerError;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
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

    private static final String GROUPS_ENDPOINT = "/api/groups";

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private GroupService groupService;

    @Test
    @DisplayName("When there are active groups, then return a list of active groups")
    void returnActiveGroups() {
        final Group[] testGroups = {
            GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE),
            GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE)
        };

        given(groupService.findActiveGroupsWithMembers()).willReturn(Flux.just(testGroups));

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

        verify(groupService).findActiveGroupsWithMembers();
    }

    @Test
    @DisplayName("Handle InternalServerError and send back corresponding response")
    void receiveInternalServerErrorWhenForUnrecognizedException() {
        given(groupService.findActiveGroupsWithMembers()).willReturn(Flux.error(new InternalServerError()));

        webTestClient
            .get()
            .uri(GROUPS_ENDPOINT)
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
            .expectBody(String.class).value(message ->
                assertThat(message).isEqualTo("Sorry, something went wrong!"));

        verify(groupService).findActiveGroupsWithMembers();
    }
}
