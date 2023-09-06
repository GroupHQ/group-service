package com.grouphq.groupservice.group.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.grouphq.groupservice.config.SecurityConfig;
import com.grouphq.groupservice.group.domain.groups.Group;
import com.grouphq.groupservice.group.domain.groups.GroupService;
import com.grouphq.groupservice.group.domain.groups.GroupStatus;
import com.grouphq.groupservice.group.testutility.GroupTestUtility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

@WebFluxTest(GroupController.class)
@Import(SecurityConfig.class)
@Tag("IntegrationTest")
class GroupControllerWebFluxTests {

    @Autowired
    WebTestClient webTestClient;

    @MockBean
    GroupService groupService;

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
}
