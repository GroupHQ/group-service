package org.grouphq.groupservice.group.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javafaker.Faker;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import org.grouphq.groupservice.config.DataConfig;
import org.grouphq.groupservice.config.SecurityConfig;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.domain.groups.repository.GroupRepository;
import org.grouphq.groupservice.group.domain.members.Member;
import org.grouphq.groupservice.group.domain.members.MemberStatus;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
import org.grouphq.groupservice.group.web.objects.egress.PublicOutboxEvent;
import org.junit.jupiter.api.BeforeEach;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest
@AutoConfigureWebTestClient
@Import({SecurityConfig.class, DataConfig.class})
@Testcontainers
@Tag("IntegrationTest")
class GroupControllerIntegrationTest {

    private static final Faker FAKER = new Faker();

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupRepository groupRepository;

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

    @BeforeEach
    void clearGroups() {
        StepVerifier.create(
            groupService.findActiveGroups()
                .flatMap(group -> groupService.updateStatus(group.id(), GroupStatus.AUTO_DISBANDED))
                .collectList()
            )
            .expectNextCount(1)
            .verifyComplete();
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
            .uri("/api/groups")
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
    @DisplayName("When there are no active groups, then return an empty list")
    void returnEmptyListWhenNoActiveGroups() {
        webTestClient
            .get()
            .uri("/api/groups")
            .exchange()
            .expectStatus().is2xxSuccessful()
            .expectBodyList(Group.class).value(retrievedGroups -> assertThat(retrievedGroups).isEmpty());
    }

    @Test
    @DisplayName("When a user has an active member, then return the active member")
    void returnCurrentMemberForUser() {
        final Group group = GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE);
        final String websocketId = UUID.randomUUID().toString();
        StepVerifier.create(
                groupRepository.save(group)
                    .flatMap(savedGroup ->
                        groupService.addMember(savedGroup.id(), "username", websocketId))
            )
            .expectNextCount(1)
            .verifyComplete();

        webTestClient
            .get()
            .uri("/api/groups/my-member")
            .header("Authorization", basicAuthHeaderValue(websocketId))
            .exchange()
            .expectStatus().is2xxSuccessful()
            .expectBody(Member.class).value(retrievedMember -> {
                assertThat(retrievedMember).isNotNull();
                assertThat(retrievedMember.websocketId()).isEqualTo(UUID.fromString(websocketId));
                assertThat(retrievedMember.memberStatus()).isEqualTo(MemberStatus.ACTIVE);
            });
    }

    @Test
    @DisplayName("When a user has no active member, then return an empty body")
    void returnEmptyBodyWhenNoCurrentMemberForUser() {
        final String websocketId = UUID.randomUUID().toString();
        webTestClient
            .get()
            .uri("/api/groups/my-member")
            .header("Authorization", basicAuthHeaderValue(websocketId))
            .exchange()
            .expectStatus().is2xxSuccessful()
            .expectBody().isEmpty();
    }

    @Test
    @DisplayName("Returns 401 when requesting current member with invalid authorization")
    void rejectBadRequest() {
        webTestClient
            .get()
            .uri("/api/groups/my-member")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody().isEmpty();
    }

    @Test
    @DisplayName("When there are active groups, then return a list of active groups as public events")
    void returnActiveGroupsAsPublicEvents() {
        final Group[] groups = {
            GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE),
            GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE),
            GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE),
            GroupTestUtility.generateFullGroupDetails(GroupStatus.AUTO_DISBANDED)
        };

        StepVerifier.create(
                groupRepository.saveAll(Flux.just(groups))
                    .flatMap(group -> {
                        if (group.status().equals(GroupStatus.ACTIVE)) {
                            return groupService.addMember(group.id(), FAKER.name().firstName(),
                                UUID.randomUUID().toString());
                        } else {
                            return Mono.empty();
                        }
                    })
            )
            .expectNextCount(Arrays.stream(groups).filter(group -> group.status().equals(GroupStatus.ACTIVE)).count())
            .verifyComplete();

        webTestClient
            .get()
            .uri("/api/groups/events")
            .exchange()
            .expectStatus().is2xxSuccessful()
            .expectBodyList(PublicOutboxEvent.class).value(retrievedGroups -> {
                assertThat(retrievedGroups).isNotEmpty();
                assertThat(retrievedGroups).allSatisfy(publicOutboxEvent -> {
                    assertThat(publicOutboxEvent.eventData()).isExactlyInstanceOf(Group.class);

                    final Group group = (Group) publicOutboxEvent.eventData();
                    assertThat(group.status()).isEqualTo(GroupStatus.ACTIVE);
                    assertThat(group.members().size()).isGreaterThan(0);
                });
            });
    }

    @Test
    @DisplayName("When there are no active groups, then return an empty list of events")
    void returnAnEmptyListOfEventsWhenNoActiveGroups() {
        final Group[] groups = {
            GroupTestUtility.generateFullGroupDetails(GroupStatus.AUTO_DISBANDED)
        };

        StepVerifier.create(groupRepository.saveAll(Flux.just(groups)))
            .expectNextCount(groups.length)
            .verifyComplete();

        webTestClient
            .get()
            .uri("/api/groups/events")
            .exchange()
            .expectStatus().is2xxSuccessful()
            .expectBodyList(PublicOutboxEvent.class).value(retrievedGroups -> assertThat(retrievedGroups).isEmpty());
    }


    private String basicAuthHeaderValue(String websocketId) {
        final String credentials = websocketId + ":";
        return "Basic " + new String(Base64.getEncoder().encode(credentials.getBytes()));
    }
}
