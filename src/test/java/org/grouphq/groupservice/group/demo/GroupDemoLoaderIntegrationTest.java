package org.grouphq.groupservice.group.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.grouphq.groupservice.config.GroupProperties;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupEventService;
import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.domain.groups.repository.GroupRepository;
import org.grouphq.groupservice.group.domain.members.MemberEventService;
import org.grouphq.groupservice.group.domain.members.MemberStatus;
import org.grouphq.groupservice.group.domain.members.repository.MemberRepository;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
    private GroupGeneratorService groupGeneratorService;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private GroupProperties groupProperties;

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupEventService groupEventService;

    @Autowired
    private MemberEventService memberEventService;
    private static final String TITLE = "Title";
    private static final String DESCRIPTION = "Description";

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
    void clearRepositories() {
        this.groupDemoLoader =
            new GroupDemoLoader(groupProperties, groupGeneratorService, groupService,
                groupEventService, memberEventService);
        StepVerifier.create(memberRepository.deleteAll().thenMany(groupRepository.deleteAll()))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Loads groups based on initial group size on first call")
    void loadsGroups() {
        final int initialGroupSize = 3;
        final int periodicGroupAdditionCount = 2;

        StepVerifier.create(
            groupDemoLoader.loadGroups(initialGroupSize, periodicGroupAdditionCount)
                .thenMany(groupService.findActiveGroups()))
            .expectNextCount(initialGroupSize)
            .verifyComplete();
        StepVerifier.create(
            groupDemoLoader.loadGroups(3, 2))
            .expectComplete()
            .verify();

        StepVerifier.create(groupRepository.getAllGroups())
            .expectNextCount(initialGroupSize + periodicGroupAdditionCount)
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Loads groups on subsequent calls based on periodic group addition count")
    void loadsGroupsOnSubsequentCalls() {
        final int initialGroupSize = 3;
        final int periodicGroupAdditionCount = 2;

        StepVerifier.create(
            groupDemoLoader.loadGroups(initialGroupSize, periodicGroupAdditionCount)
                .thenMany(groupDemoLoader.loadGroups(initialGroupSize, periodicGroupAdditionCount)
                .thenMany(groupDemoLoader.loadGroups(initialGroupSize, periodicGroupAdditionCount)))
                .thenMany(groupService.findActiveGroups())
            )
            .expectNextCount(initialGroupSize + (periodicGroupAdditionCount * 2))
            .verifyComplete();
    }

    @Test
    @DisplayName("Active members in expired groups have their status changed to AUTO_LEFT")
    void autoLeaveMembersInExpiredGroups() {
        StepVerifier.create(
            groupService.createGroup(TITLE, DESCRIPTION, 5)
                .flatMap(group -> groupService.addMember(group.id(), "Member 1", UUID.randomUUID().toString())
                    .then(groupService.addMember(group.id(), "Member 2", UUID.randomUUID().toString()))
                    .thenReturn(group)
                )
                .flatMap(group -> groupDemoLoader.expireGroupsCreatedBefore(group.createdDate())
                    .then(groupService.findGroupByIdWithAllMembers(group.id()))
                )
        )
            .assertNext(group -> assertThat(group.members())
                .hasSize(2)
                .allMatch(member -> member.memberStatus().equals(MemberStatus.AUTO_LEFT)))
            .verifyComplete();
    }

    @Test
    @DisplayName("Expires groups with time older than cutoff time")
    void expiresGroups() {
        StepVerifier.create(
            groupService.createGroup(TITLE, DESCRIPTION, 5)
                .then(groupService.createGroup(TITLE, DESCRIPTION, 5))
                .then(groupService.createGroup(TITLE, DESCRIPTION, 5))
                .flatMapMany(group -> groupDemoLoader.expireGroupsCreatedBefore(group.createdDate()))
                .thenMany(groupService.findActiveGroups())
        )
            .expectNextCount(0)
            .verifyComplete();
    }

    @Test
    @DisplayName("Does not expire groups after cutoff time")
    void expirationStatusJob() {
        Group[] testGroups = new Group[3];

        for (int i = 0; i < testGroups.length; i++) {
            testGroups[i] = GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE);
        }

        StepVerifier.create(groupRepository.saveAll(Flux.just(testGroups)))
            .expectNextCount(3)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        final Instant cutoffDate = Instant.now().minus(1, ChronoUnit.SECONDS);
        StepVerifier.create(groupDemoLoader.expireGroupsCreatedBefore(cutoffDate))
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

    @Test
    @DisplayName("Load members into active groups")
    void loadMembersIntoActiveGroups() {
        final int initialGroupSize = 3;
        final int periodicGroupAdditionCount = 2;
        final int memberJoinMaxDelay = 0;

        StepVerifier.create(
            groupDemoLoader.loadGroups(initialGroupSize, periodicGroupAdditionCount)
                .thenMany(groupDemoLoader.loadMembers(memberJoinMaxDelay))
                .thenMany(groupDemoLoader.loadMembers(memberJoinMaxDelay))
                .thenMany(groupService.findActiveGroupsWithMembers()).collectList())
            .assertNext(groups ->
                assertThat(groups).allSatisfy(group ->
                    assertThat(group.members().size())
                        .isEqualTo(3))) // includes member added during group creation
            .verifyComplete();
    }
}