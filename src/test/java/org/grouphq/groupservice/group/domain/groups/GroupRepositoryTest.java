package org.grouphq.groupservice.group.domain.groups;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.grouphq.groupservice.config.DataConfig;
import org.grouphq.groupservice.group.domain.groups.repository.GroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;
import reactor.util.function.Tuples;

@DataR2dbcTest
@Import(DataConfig.class)
@Testcontainers
@ActiveProfiles("test")
@Tag("IntegrationTest")
class GroupRepositoryTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.4"));

    @Autowired
    private GroupRepository groupRepository;

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

    @Test
    @DisplayName("Get all active groups from database")
    void retrievesOnlyActiveGroups() {
        StepVerifier.create(groupRepository.findGroupsByStatus(GroupStatus.ACTIVE).collectList())
            .assertNext(groups -> {
                assertThat(groups).isNotEmpty();
                assertThat(groups).allMatch(group -> group.status() == GroupStatus.ACTIVE);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Get all groups from database")
    void retrievesAllGroups() {
        StepVerifier.create(groupRepository.getAllGroups().collectList())
            .assertNext(groups -> {
                assertThat(groups).isNotEmpty();
                assertThat(groups).anyMatch(group -> group.status() == GroupStatus.ACTIVE);
                assertThat(groups).anyMatch(group -> group.status() == GroupStatus.AUTO_DISBANDED);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Get all active groups older than cutoff date")
    void retrieveGroupsThatShouldExpire() {
        final Instant cutoffDate = Instant.now();

        StepVerifier.create(
            groupRepository
                .getActiveGroupsPastCutoffDate(cutoffDate, GroupStatus.ACTIVE).collectList())
            .assertNext(groups -> {
                assertThat(groups).isNotEmpty();
                assertThat(groups).allMatch(group -> group.createdDate().isBefore(cutoffDate));
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Updates group's last updated time when the group is created or updated")
    void updateGroupsLastActiveTime() {
        final Group group = Group.of("Title", "Description", 10, GroupStatus.ACTIVE);
        final Instant testStartTime = Instant.now();

        StepVerifier.create(groupRepository.save(group)
            .flatMap(savedGroup -> {
                assertThat(savedGroup.lastModifiedDate()).isNotNull();

                final Group updatedGroup = savedGroup.withDescription("Updated Description");

                return groupRepository.save(updatedGroup)
                    .map(updated -> Tuples.of(savedGroup, updated));
            }))
            .assertNext(groupTuple -> {
                assertThat(groupTuple.getT1().lastModifiedDate())
                    .isAfter(testStartTime);
                assertThat(groupTuple.getT2().lastModifiedDate())
                    .isAfter(groupTuple.getT1().lastModifiedDate());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Updates group's status")
    void updateGroupStatus() {
        final List<Group> groups = new ArrayList<>();

        StepVerifier.create(groupRepository.findGroupsByStatus(GroupStatus.ACTIVE).collectList())
            .consumeNextWith(groups::addAll)
            .verifyComplete();

        assertThat(groups).allMatch(group -> group.status() == GroupStatus.ACTIVE);

        final Long groupId = groups.get(0).id();

        StepVerifier.create(groupRepository
                .updateStatusByGroupId(groupId, GroupStatus.AUTO_DISBANDED))
            .assertNext(updatedGroup ->
                assertThat(updatedGroup.status()).isEqualTo(GroupStatus.AUTO_DISBANDED))
            .verifyComplete();
    }
    
    @Test
    @DisplayName("Updates group's last updated time when group status updates")
    void updateGroupStatusUpdatesLastModifiedDate() {
        StepVerifier.create(
            groupRepository.findGroupsByStatus(GroupStatus.ACTIVE)
                .take(1)
                .flatMap(group ->
                    groupRepository.updateStatusByGroupId(group.id(), GroupStatus.AUTO_DISBANDED)
                        .map(updatedGroup -> Tuples.of(group, updatedGroup))
                )
            )
            .assertNext(tuple2 -> {
                final Group groupBeforeUpdate = tuple2.getT1();
                final Group groupAfterUpdate = tuple2.getT2();
                assertThat(groupAfterUpdate.lastModifiedDate())
                    .isAfter(groupBeforeUpdate.lastModifiedDate());
            })
            .verifyComplete();
    }
}
