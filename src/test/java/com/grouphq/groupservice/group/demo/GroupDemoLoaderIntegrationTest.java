package com.grouphq.groupservice.group.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.grouphq.groupservice.config.DataConfig;
import com.grouphq.groupservice.group.domain.groups.Group;
import com.grouphq.groupservice.group.domain.groups.GroupRepository;
import com.grouphq.groupservice.group.domain.groups.GroupService;
import com.grouphq.groupservice.group.domain.groups.GroupStatus;
import com.grouphq.groupservice.group.testutility.GroupTestUtility;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
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
@Import(DataConfig.class)
@TestPropertySource(properties = {
    "group.loader.initial-group-size=3",
    "group.loader.initial-group-delay=10000",
    "group.loader.periodic-group-addition-interval=10000",
    "group.expiry-checker.time=1800",
    "group.expiry-checker.initial-check-delay=10000",
    "group.expiry-checker.check-interval=10000"
})
@Testcontainers
@Tag("IntegrationTest")
class GroupDemoLoaderIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.4"));

    @Autowired
    GroupDemoLoader groupDemoLoader;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    GroupService groupService;

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
        StepVerifier.create(groupDemoLoader.loadGroups())
            .expectNextCount(initialGroupSize)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        StepVerifier.create(groupDemoLoader.loadGroups())
            .expectNextCount(periodicGroupAdditionCount)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        StepVerifier.create(groupRepository.getAllGroups())
            .expectNextCount(initialGroupSize + periodicGroupAdditionCount)
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Expires groups with time older than allowed expiry time")
    void expiresGroups() {
        Group[] testGroups = new Group[3];

        for (int i = 0; i < testGroups.length; i++) {
            testGroups[i] = GroupTestUtility.generateFullGroupDetails();
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
                group.lastActive(), Instant.ofEpochMilli(0), group.lastModifiedDate(),
                group.createdBy(), group.lastModifiedBy(), group.version()
            );
        }

        StepVerifier.create(groupRepository.saveAll(Flux.just(groupsToExpire)))
            .expectNextCount(3)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        StepVerifier.create(groupDemoLoader.expireGroups())
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
    @DisplayName("Does not expire groups with time younger than allowed expiry time")
    void expirationStatusJob() {
        Group[] testGroups = new Group[3];

        for (int i = 0; i < testGroups.length; i++) {
            testGroups[i] = GroupTestUtility.generateFullGroupDetails();
        }

        StepVerifier.create(groupRepository.saveAll(Flux.just(testGroups)))
            .expectNextCount(3)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        groupDemoLoader.expireGroups();

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
