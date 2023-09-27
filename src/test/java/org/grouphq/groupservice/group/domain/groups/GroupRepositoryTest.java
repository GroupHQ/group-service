package org.grouphq.groupservice.group.domain.groups;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.grouphq.groupservice.config.DataConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DataR2dbcTest
@Import(DataConfig.class)
@Testcontainers
@Tag("IntegrationTest")
class GroupRepositoryTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.4"));

    @Autowired
    private GroupRepository groupRepository;

    private static Group[] testGroups;

    @DynamicPropertySource
    private static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", GroupRepositoryTest::r2dbcUrl);
        registry.add("spring.r2dbc.username", POSTGRESQL_CONTAINER::getUsername);
        registry.add("spring.r2dbc.password", POSTGRESQL_CONTAINER::getPassword);
        registry.add("spring.flyway.url", POSTGRESQL_CONTAINER::getJdbcUrl);
    }

    private static String r2dbcUrl() {
        return String.format("r2dbc:postgresql://%s:%s/%s", POSTGRESQL_CONTAINER.getHost(),
            POSTGRESQL_CONTAINER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
            POSTGRESQL_CONTAINER.getDatabaseName());
    }

    @BeforeAll
    static void setUp() {
        testGroups = new Group[] {
            Group.of("Example Title", "Example Description", 10,
                1, GroupStatus.ACTIVE),
            Group.of("Example Title", "Example Description", 5,
                2, GroupStatus.ACTIVE),
            Group.of("Example Title", "Example Description", 5,
                2, GroupStatus.AUTO_DISBANDED)
        };
    }

    @BeforeEach
    void setUpRepositoryData() {
        StepVerifier.create(
            Mono.when(
                groupRepository.save(testGroups[0]),
                groupRepository.save(testGroups[1]),
                groupRepository.save(testGroups[2])
            )
        ).expectComplete().verify(Duration.ofSeconds(1));
    }

    @AfterEach
    void deleteRepositoryData() {
        StepVerifier.create(groupRepository.deleteAll())
            .expectComplete().verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Get all active groups from database")
    void retrievesOnlyActiveGroups() {
        final List<Group> groupsReturned = new ArrayList<>();

        StepVerifier.create(groupRepository.findGroupsByStatus(GroupStatus.ACTIVE))
            .recordWith(() -> groupsReturned)
            .expectNextCount(2)
            .expectComplete().verify(Duration.ofSeconds(1));

        assertThat(groupsReturned)
            .hasSize(2);
    }

    @Test
    @DisplayName("Get all groups from database")
    void retrievesAllGroups() {
        final List<Group> groupsReturned = new ArrayList<>();

        StepVerifier.create(groupRepository.getAllGroups())
            .recordWith(() -> groupsReturned)
            .expectNextCount(3)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        assertThat(groupsReturned)
            .hasSize(3)
            .filteredOn(group -> group.status() == GroupStatus.ACTIVE)
            .hasSize(2);

        assertThat(groupsReturned)
            .filteredOn(group -> group.status() == GroupStatus.AUTO_DISBANDED)
            .hasSize(1);
    }

    @Test
    @DisplayName("Get all groups older than cutoff date")
    void retrieveGroupsThatShouldExpire() {
        final List<Group> groupsReturned = new ArrayList<>();

        final Instant cutoffDate = Instant.now();

        StepVerifier.create(
            groupRepository.getActiveGroupsPastCutoffDate(cutoffDate, GroupStatus.ACTIVE))
            .recordWith(() -> groupsReturned)
            .expectNextCount(2)
            .expectComplete().verify(Duration.ofSeconds(1));

        assertThat(groupsReturned)
            .filteredOn(group -> group.createdDate().isBefore(cutoffDate))
            .hasSize(2);
    }

    @Test
    @DisplayName("Expires groups older than cutoff date")
    void expiresGroupsBeforeCutoffDate() {
        final List<Group> groupsReturned = new ArrayList<>();

        groupRepository.expireGroupsPastCutoffDate(Instant.now(), GroupStatus.AUTO_DISBANDED)
            .thenMany(groupRepository.findGroupsByStatus(GroupStatus.AUTO_DISBANDED))
            .as(StepVerifier::create)
            .recordWith(() -> groupsReturned)
            .expectNextCount(3)
            .expectComplete().verify(Duration.ofSeconds(1));

        assertThat(groupsReturned)
            .filteredOn(group -> group.status() == GroupStatus.AUTO_DISBANDED)
            .hasSize(3);
    }

    @Test
    @DisplayName("Updates group's last active time when group is created or updates")
    void updateGroupsLastActiveTime() {
        AtomicReference<Group> group = new AtomicReference<>(Group.of("Title", "Description",
            10, 5, GroupStatus.ACTIVE));

        final Instant lastActiveInitial = group.get().lastActive();

        StepVerifier.create(groupRepository.save(group.get()))
            .consumeNextWith(group::set)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        // We have to get the group again since the trigger updating the active time runs after
        // we retrieve it from the database in the previous step.
        StepVerifier.create(groupRepository.findById(group.get().id()))
            .consumeNextWith(group::set)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        final Instant lastActiveGroupCreated = group.get().lastActive();
        assertThat(lastActiveGroupCreated).isAfter(lastActiveInitial);

        group = new AtomicReference<>(new Group(
            group.get().id(), group.get().title(), "New Description", group.get().maxGroupSize(),
            group.get().currentGroupSize(), group.get().status(), group.get().lastActive(),
            group.get().createdDate(), group.get().lastModifiedDate(), group.get().createdBy(),
            group.get().lastModifiedBy(), group.get().version()
        ));

        StepVerifier.create(groupRepository.save(group.get()))
            .consumeNextWith(group::set)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        StepVerifier.create(groupRepository.findById(group.get().id()))
            .consumeNextWith(group::set)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        final Instant lastActiveGroupUpdated = group.get().lastActive();
        assertThat(lastActiveGroupUpdated).isAfter(lastActiveGroupCreated);
    }

    @Test
    @DisplayName("Updates group's status")
    void updateGroupStatus() {
        final List<Group> groups = new ArrayList<>();

        StepVerifier.create(groupRepository.findGroupsByStatus(GroupStatus.ACTIVE))
            .recordWith(() -> groups)
            .expectNextCount(2)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        assertThat(groups).allMatch(group -> group.status() == GroupStatus.ACTIVE);

        final Long groupId = groups.get(0).id();

        StepVerifier.create(groupRepository.updateStatus(groupId, GroupStatus.AUTO_DISBANDED))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        StepVerifier.create(groupRepository.findById(groupId))
            .consumeNextWith(group ->
                assertThat(group.status()).isEqualTo(GroupStatus.AUTO_DISBANDED))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Updates the status of several groups before a certain date")
    void updateGroupStatusBeforeDate() {
        final List<Group> groups = new ArrayList<>();

        StepVerifier.create(groupRepository.findGroupsByStatus(GroupStatus.ACTIVE))
            .recordWith(() -> groups)
            .expectNextCount(2)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        assertThat(groups).allMatch(group -> group.status() == GroupStatus.ACTIVE);

        StepVerifier.create(
            groupRepository.expireGroupsPastCutoffDate(Instant.now(), GroupStatus.AUTO_DISBANDED))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        groups.clear();
        StepVerifier.create(groupRepository.findAll())
            .recordWith(() -> groups)
            .expectNextCount(3)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        assertThat(groups).allMatch(
            group -> group.status() == GroupStatus.AUTO_DISBANDED);
    }

}
