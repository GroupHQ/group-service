package org.grouphq.groupservice.group.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupRepository;
import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Tests GroupDemoLoader's method logic across GroupService, GroupRepository and the Database.
 * This does not check Spring's scheduling logic.
 * Properties for Spring's scheduling are overridden to prevent it from running during this test.
 */
@SpringBootTest
@Testcontainers
@Tag("IntegrationTest")
class GroupDemoLoaderIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.4"));

    private GroupDemoLoader groupDemoLoader;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupService groupService;

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", GroupDemoLoaderIntegrationTest::r2dbcUrl);
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
    void timesJobShouldHaveRun() {
        this.groupDemoLoader = new GroupDemoLoader(groupService);
        StepVerifier.create(groupRepository.deleteAll())
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Loads groups to database based on external properties")
    void loadsGroups(
        @Value("${group.loader.initial-group-size}")
        int initialGroupSize,

        @Value("${group.loader.periodic-group-addition-count}")
        int periodicGroupAdditionCount
    ) {
        StepVerifier.create(
            groupDemoLoader.loadGroups(initialGroupSize, periodicGroupAdditionCount))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        StepVerifier.create(
            groupDemoLoader.loadGroups(initialGroupSize, periodicGroupAdditionCount))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        StepVerifier.create(groupRepository.getAllGroups())
            .expectNextCount(initialGroupSize + periodicGroupAdditionCount)
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Expires groups with time older than cutoff time")
    void expiresGroups(@Value ("${group.cutoff-checker.time}") int cutoffTime) {
        final Instant cutoffDate = Instant.now().minus(cutoffTime, ChronoUnit.SECONDS);
        final Group[] testGroups = new Group[3];

        for (int i = 0; i < testGroups.length; i++) {
            testGroups[i] = GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE);
        }

        final List<Group> groupsSaved = new ArrayList<>();

        StepVerifier.create(groupRepository.saveAll(Flux.just(testGroups)))
            .recordWith(() -> groupsSaved)
            .expectNextCount(3)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        Group[] groupsToExpire = new Group[3];
        for (int i = 0; i < groupsSaved.size(); i++) {
            final Group group = groupsSaved.get(i);
            groupsToExpire[i] = new Group(
                group.id(), group.title(), group.description(),
                group.maxGroupSize(), group.currentGroupSize(), group.status(),
                group.lastActive(), cutoffDate.minus(1, ChronoUnit.SECONDS),
                group.lastModifiedDate(), group.createdBy(),
                group.lastModifiedBy(), group.version()
            );
        }

        StepVerifier.create(groupRepository.saveAll(Flux.just(groupsToExpire)))
            .expectNextCount(3)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        StepVerifier.create(groupDemoLoader.expireGroups(cutoffDate))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        final List<Group> groups = new ArrayList<>();

        StepVerifier.create(groupRepository.getAllGroups())
            .recordWith(() -> groups)
            .expectNextCount(3)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        assertThat(groups)
            .filteredOn(group -> group.status().equals(GroupStatus.AUTO_DISBANDED))
            .hasSize(groups.size());
    }

    @Test
    @DisplayName("Does not expire groups after cutoff time")
    void expirationStatusJob(@Value ("${group.cutoff-checker.time}") int cutoffTime) {
        Group[] testGroups = new Group[3];

        for (int i = 0; i < testGroups.length; i++) {
            testGroups[i] = GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE);
        }

        StepVerifier.create(groupRepository.saveAll(Flux.just(testGroups)))
            .expectNextCount(3)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        final Instant cutoffDate = Instant.now().minus(cutoffTime, ChronoUnit.SECONDS);
        StepVerifier.create(groupDemoLoader.expireGroups(cutoffDate))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        final List<Group> groups = new ArrayList<>();

        StepVerifier.create(groupRepository.getAllGroups())
            .recordWith(() -> groups)
            .expectNextCount(3)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        assertThat(groups)
            .filteredOn(group -> group.status().equals(GroupStatus.ACTIVE))
            .hasSize(groups.size());
    }
}