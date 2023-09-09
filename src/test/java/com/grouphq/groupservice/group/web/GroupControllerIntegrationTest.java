package com.grouphq.groupservice.group.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.grouphq.groupservice.config.DataConfig;
import com.grouphq.groupservice.config.SecurityConfig;
import com.grouphq.groupservice.group.domain.groups.Group;
import com.grouphq.groupservice.group.domain.groups.GroupRepository;
import com.grouphq.groupservice.group.domain.groups.GroupStatus;
import com.grouphq.groupservice.group.domain.members.Member;
import com.grouphq.groupservice.group.domain.members.MemberStatus;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

@SpringBootTest
@AutoConfigureWebTestClient
@Import({SecurityConfig.class, DataConfig.class})
@Testcontainers
@Tag("IntegrationTest")
class GroupControllerIntegrationTest {

    static final String JOIN_GROUP_ENDPOINT = "/groups/join";
    static final String LEAVE_GROUP_ENDPOINT = "/groups/leave";

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.4"));

    @Autowired
    GroupController groupController;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    WebTestClient webTestClient;

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
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
    @DisplayName("Allow user to join an active group that's not full")
    void allowUserToJoinActiveGroupNotFull() {
        final Long groupId = createGroup(Group.of("Title 1", "Description 1",
            5, 4, GroupStatus.ACTIVE));

        final GroupJoinRequest groupJoinRequest = new GroupJoinRequest("User 1", groupId);

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

        webTestClient
            .get()
            .uri("/groups/" + groupId + "/members")
            .exchange()
            .expectStatus().is2xxSuccessful()
            .expectBodyList(Member.class).value(members -> {
                assertThat(members).hasSize(1);
                assertThat(members.get(0).username()).isEqualTo("User 1");
            });
    }

    @Test
    @DisplayName("Allow users to leave groups")
    void leaveGroup() {
        final Long groupId = createGroup(Group.of("I'm leaving!", "Description ZYZ",
            5, 4, GroupStatus.ACTIVE));

        // Join group
        final GroupJoinRequest groupJoinRequest = new GroupJoinRequest("Ephemeral User", groupId);
        final AtomicReference<Long> memberId = new AtomicReference<>();
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
                memberId.set(memberCreated.id());
            });

        // Leave group
        final GroupLeaveRequest groupLeaveRequest = new GroupLeaveRequest(memberId.get());
        webTestClient
            .post()
            .uri(LEAVE_GROUP_ENDPOINT)
            .bodyValue(groupLeaveRequest)
            .exchange()
            .expectStatus().isNoContent();

        // Make sure member is gone
        webTestClient
            .get()
            .uri("/groups/" + groupId + "/members")
            .exchange()
            .expectStatus().is2xxSuccessful()
            .expectBodyList(Member.class).value(members -> assertThat(members).hasSize(0));
    }

    @Test
    @DisplayName("Do not allow users to join non-active groups")
    void receiveGroupNotActiveExceptionWhenTryingToJoinNonActiveGroup() {
        final Long groupId = createGroup(Group.of("Title 2", "Description 2",
            5, 4, GroupStatus.DISBANDED));

        final GroupJoinRequest groupJoinRequest = new GroupJoinRequest("User 2", groupId);

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
    @DisplayName("Do not allow users to join full groups")
    void receiveGroupIsFullExceptionWhenTryingToJoinFullGroup() {
        final Long groupId = createGroup(Group.of("Title 3", "Description 3",
            5, 5, GroupStatus.ACTIVE));

        final GroupJoinRequest groupJoinRequest = new GroupJoinRequest("User 3", groupId);

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
    @DisplayName("Do not allow users to join non-active and full groups.")
    void receiveGroupNotActiveExceptionWhenTryingToJoinNonActiveFullGroup() {
        final Long groupId = createGroup(Group.of("Title 4", "Description 4",
            5, 5, GroupStatus.DISBANDED));

        final GroupJoinRequest groupJoinRequest = new GroupJoinRequest("User 4", groupId);

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

    private Long createGroup(Group newGroup) {
        final AtomicReference<Long> groupId = new AtomicReference<>();

        StepVerifier.create(groupRepository.save(newGroup))
            .consumeNextWith(group -> groupId.set(group.id()))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        return groupId.get();
    }

}
