package com.grouphq.groupservice.group.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.grouphq.groupservice.config.DataConfig;
import com.grouphq.groupservice.config.SecurityConfig;
import com.grouphq.groupservice.group.domain.groups.Group;
import com.grouphq.groupservice.group.domain.groups.GroupRepository;
import com.grouphq.groupservice.group.domain.groups.GroupStatus;
import com.grouphq.groupservice.group.domain.members.Member;
import com.grouphq.groupservice.group.domain.members.MemberRepository;
import com.grouphq.groupservice.group.testutility.GroupTestUtility;
import com.grouphq.groupservice.group.web.objects.egress.PublicMember;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SpringBootTest
@AutoConfigureWebTestClient
@Import({SecurityConfig.class, DataConfig.class})
@Testcontainers
@Tag("IntegrationTest")
class GroupControllerIntegrationTest {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private WebTestClient webTestClient;

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.4"));

    @DynamicPropertySource
    private static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", GroupControllerIntegrationTest::r2dbcUrl);
        registry.add("spring.r2dbc.username", POSTGRESQL_CONTAINER::getUsername);
        registry.add("spring.r2dbc.password", POSTGRESQL_CONTAINER::getPassword);
        registry.add("spring.flyway.url", POSTGRESQL_CONTAINER::getJdbcUrl);
    }

    private static String r2dbcUrl() {
        return String.format("r2dbc:postgresql://%s:%s/%s", POSTGRESQL_CONTAINER.getHost(),
            POSTGRESQL_CONTAINER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
            POSTGRESQL_CONTAINER.getDatabaseName());
    }

    @Test
    @DisplayName("When there are active groups, then return a list of active groups")
    void returnActiveGroups() {
        final Group[] groups = {
            GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE),
            GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE),
            GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE)
        };

        StepVerifier.create(groupRepository.saveAll(Flux.just(groups)))
            .expectNextCount(3)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        webTestClient
            .get()
            .uri("/groups")
            .exchange()
            .expectStatus().is2xxSuccessful()
            .expectBodyList(Group.class).value(retrievedGroups -> {
                assertThat(retrievedGroups).isNotEmpty();
                assertThat(retrievedGroups).allMatch(group ->
                        group.status().equals(GroupStatus.ACTIVE),
                    "All groups received should be active");
            });
    }

    @Test
    @DisplayName("Allow users to retrieve active group members as public")
    void retrieveActiveGroupMembersAsPublic() {
        final Long groupId = createGroup(Group.of("Populated Group", "We got pumpkins!",
            5, 0, GroupStatus.ACTIVE));

        final Member[] members = {
            Member.of("User 1", groupId),
            Member.of("User 2", groupId),
            Member.of("User 3", groupId)
        };

        StepVerifier.create(memberRepository.saveAll(Flux.just(members)))
            .expectNextCount(3)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        final List<Member> memberList = new ArrayList<>();

        StepVerifier.create(memberRepository.getActiveMembersByGroup(groupId))
            .recordWith(() -> memberList)
            .expectNextCount(3)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        final List<PublicMember> publicMembers = List.of(
            new PublicMember(memberList.get(0).username(), memberList.get(0).groupId(),
                memberList.get(0).memberStatus(), memberList.get(0).joinedDate(),
                memberList.get(0).exitedDate()),
            new PublicMember(memberList.get(1).username(), memberList.get(1).groupId(),
                memberList.get(1).memberStatus(), memberList.get(1).joinedDate(),
                memberList.get(1).exitedDate()),
            new PublicMember(memberList.get(2).username(), memberList.get(2).groupId(),
                memberList.get(2).memberStatus(), memberList.get(2).joinedDate(),
                memberList.get(2).exitedDate())
        );

        webTestClient
            .get()
            .uri("/groups/" + groupId + "/members")
            .exchange()
            .expectStatus().is2xxSuccessful()
            .expectBodyList(PublicMember.class).value(retrievedMembers -> {
                assertThat(retrievedMembers)
                    .containsExactlyInAnyOrderElementsOf(publicMembers);
                assertThat(retrievedMembers).isSortedAccordingTo((memberA, memberB) -> {
                    if (memberA.joinedDate().isBefore(memberB.joinedDate())) {
                        return -1;
                    } else if (memberA.joinedDate().isAfter(memberB.joinedDate())) {
                        return 1;
                    } else {
                        return 0;
                    }
                });
            });
    }

    private Long createGroup(Group newGroup) {
        final AtomicReference<Long> groupId = new AtomicReference<>();

        StepVerifier.create(groupRepository.save(newGroup))
            .consumeNextWith(group -> groupId.set(group.id()))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        return groupId.get();
    }
}
