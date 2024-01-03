package org.grouphq.groupservice.group.domain.groups;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.grouphq.groupservice.group.domain.exceptions.GroupNotActiveException;
import org.grouphq.groupservice.group.domain.exceptions.GroupSizeException;
import org.grouphq.groupservice.group.domain.exceptions.MemberNotFoundException;
import org.grouphq.groupservice.group.domain.exceptions.UserAlreadyInGroupException;
import org.grouphq.groupservice.group.domain.members.MemberStatus;
import org.grouphq.groupservice.group.domain.members.repository.MemberRepository;
import org.grouphq.groupservice.group.web.objects.egress.PublicMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Tag("IntegrationTest")
class GroupServiceTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.4"));

    @Autowired
    private GroupService groupService;

    @Autowired
    private MemberRepository memberRepository;
    
    private static final String USER = "User";
    private static final String GROUP = "Group";
    private static final String GROUP_DESCRIPTION = "Group Description";

    @DynamicPropertySource
    private static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", GroupServiceTest::r2dbcUrl);
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
    @DisplayName("Create a group")
    void createsGroup() {
        final String title = "Group 1";
        final String description = "Group 1 Description";
        final int maxGroupSize = 10;

        final Mono<Group> groupMono =
            groupService.createGroup(title, description, maxGroupSize)
                    .flatMap(group -> groupService.findGroupById(group.id()));

        StepVerifier.create(groupMono).assertNext(group -> {
            assertThat(group.title()).isEqualTo(title);
            assertThat(group.description()).isEqualTo(description);
            assertThat(group.maxGroupSize()).isEqualTo(maxGroupSize);
            assertThat(group.status()).isEqualTo(GroupStatus.ACTIVE);
            assertThat(group.createdDate()).isBetween(Instant.now().minus(Duration.ofSeconds(5)), Instant.now());
            assertThat(group.lastModifiedDate())
                .isBetween(Instant.now().minus(Duration.ofSeconds(5)), Instant.now());
            assertThat(group.createdBy()).isNotEmpty();
            assertThat(group.lastModifiedBy()).isNotEmpty();
            assertThat(group.lastModifiedBy()).isEqualTo(group.createdBy());
            assertThat(group.version()).isOne();
            assertThat(group.members()).isEmpty();
        })
            .verifyComplete();
    }

    @Test
    @DisplayName("Get all active groups from database")
    void retrievesOnlyActiveGroups() {
        StepVerifier.create(groupService.findActiveGroups().collectList())
            .assertNext(groups -> assertThat(groups).allMatch(group -> group.status() == GroupStatus.ACTIVE))
            .verifyComplete();
    }

    @Test
    @DisplayName("Get all groups older than cutoff date")
    void retrieveGroupsThatShouldExpire() {
        final Instant testStartDate = Instant.now();
        StepVerifier.create(groupService.findActiveGroupsCreatedBefore(testStartDate).collectList())
            .assertNext(groups -> assertThat(groups).allMatch(group -> group.createdDate().isBefore(testStartDate)))
            .verifyComplete();
    }

    @Test
    @DisplayName("Updates group's last updated time when group is created or updates")
    void updateGroupsLastActiveTime() {
        StepVerifier.create(groupService.createGroup(GROUP, GROUP_DESCRIPTION, 10)
                .flatMap(savedGroup ->
                    groupService.updateStatus(savedGroup.id(), GroupStatus.DISBANDED)
                    .then(Mono.zip(
                        Mono.just(savedGroup),
                        groupService.findGroupById(savedGroup.id())
                    ))
            ))
            .assertNext(tuple2 -> {
                final Group savedGroup = tuple2.getT1();
                final Group updatedGroup = tuple2.getT2();
                assertThat(savedGroup.version()).isNotEqualTo(updatedGroup.version());
                assertThat(updatedGroup.lastModifiedDate()).isAfter(savedGroup.lastModifiedDate());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Updates status of a group")
    void updateGroupStatus() {
        StepVerifier.create(
                groupService.findActiveGroups().take(1)
                    .flatMap(group ->
                        groupService.updateStatus(group.id(), GroupStatus.AUTO_DISBANDED)
                            .then(groupService.findGroupById(group.id()))
                    )
            )
            .assertNext(group -> assertThat(group.status()).isEqualTo(GroupStatus.AUTO_DISBANDED))
            .verifyComplete();
    }

    @Test
    @DisplayName("Fetch active members from a group")
    void fetchActiveMembersFromGroup() {
        StepVerifier.create(
            groupService.createGroup(GROUP, GROUP_DESCRIPTION, 10)
                .flatMap(group ->
                    Flux.range(1, 5)
                        .concatMap(i ->
                            groupService.addMember(group.id(), "user" + i, UUID.randomUUID().toString()))
                        .then(Mono.just(group))
                )
                .flatMap(group -> groupService.findGroupByIdWithActiveMembers(group.id()))
        )
        .assertNext(group -> {
            assertThat(group.members()).hasSize(5);
            assertThat(group.members()).allMatch(member -> member.memberStatus() == MemberStatus.ACTIVE);
        })
            .verifyComplete();
    }

    @Test
    @DisplayName("Adds a member to a group")
    void addMemberToGroup() {
        StepVerifier.create(
            groupService.createGroup(GROUP, GROUP_DESCRIPTION, 10)
                .flatMap(group ->
                    groupService.addMember(group.id(), USER, UUID.randomUUID().toString()))
                .flatMap(member ->
                    groupService.findGroupByIdWithActiveMembers(member.groupId()))
        )
            .assertNext(group ->
                assertThat(group.members()).allMatch(member -> member.memberStatus() == MemberStatus.ACTIVE))
            .verifyComplete();
    }

    @Test
    @DisplayName("Sets member status to ACTIVE when adding a member")
    void setMemberStatusToActiveWhenAddingMember() {
        StepVerifier.create(
                groupService.createGroup(GROUP, GROUP_DESCRIPTION, 10)
                    .flatMap(group ->
                        groupService.addMember(group.id(), USER, UUID.randomUUID().toString()))
                    .flatMap(member ->
                        groupService.findGroupByIdWithActiveMembers(member.groupId()))
            )
            .assertNext(group -> {
                assertThat(group.members()).hasSize(1);
                assertThat(group.members()).allMatch(member -> member.memberStatus() == MemberStatus.ACTIVE);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Sets member joined date when adding a member")
    void setMemberJoinedDateWhenAddingMember() {
        final Instant testStartTime = Instant.now();
        StepVerifier.create(
                groupService.createGroup(GROUP, GROUP_DESCRIPTION, 10)
                    .flatMap(group ->
                        groupService.addMember(group.id(), USER, UUID.randomUUID().toString()))
                    .flatMap(member ->
                        groupService.findGroupByIdWithActiveMembers(member.groupId()))
            )
            .assertNext(group -> {
                final PublicMember member = group.members().get(0);
                assertThat(member).isNotNull();
                assertThat(member.joinedDate()).isBetween(testStartTime, Instant.now());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Does not allow member additions to groups that are not active")
    void doesNotAllowMemberAdditionsToNonActiveGroups() {
        StepVerifier.create(
                groupService.createGroup(GROUP, GROUP_DESCRIPTION, 10)
                    .flatMap(group ->
                        groupService.updateStatus(group.id(), GroupStatus.DISBANDED).thenReturn(group))
                    .flatMap(group ->
                        groupService.addMember(group.id(), USER, UUID.randomUUID().toString()))
            )
            .expectError(GroupNotActiveException.class)
            .verify();
    }

    @Test
    @DisplayName("Does not add a member to a group if the group is full")
    void doesNotAddMemberToFullGroup() {
        StepVerifier.create(
                groupService.createGroup(GROUP, GROUP_DESCRIPTION, 2)
                    .flatMap(group ->
                        groupService.addMember(group.id(), USER, UUID.randomUUID().toString())
                            .then(groupService.addMember(group.id(), "user2", UUID.randomUUID().toString()))
                            .then(groupService.addMember(group.id(), "user3", UUID.randomUUID().toString()))
                            .thenReturn(group))
                    .flatMap(group -> groupService.findGroupByIdWithActiveMembers(group.id()))
            )
            .expectError(GroupSizeException.class)
            .verify();
    }

    @Test
    @DisplayName("Remove a member from a group")
    void removeMemberFromGroup() {
        StepVerifier.create(
                groupService.createGroup(GROUP, GROUP_DESCRIPTION, 10)
                    .flatMap(group ->
                        groupService.addMember(group.id(), USER, UUID.randomUUID().toString())
                        .flatMap(member ->
                            groupService.removeMember(member.groupId(), member.id(), member.websocketId().toString())
                            .then(groupService.findGroupByIdWithActiveMembers(member.groupId()))
                        )
                    )
            )
            .assertNext(group -> assertThat(group.members()).hasSize(0))
            .verifyComplete();
    }

    @Test
    @DisplayName("Sets member status to LEFT when removing a member")
    void setMemberStatusToLeftWhenRemovingMember() {
        StepVerifier.create(
                groupService.createGroup(GROUP, GROUP_DESCRIPTION, 10)
                    .flatMap(group ->
                        groupService.addMember(group.id(), USER, UUID.randomUUID().toString())
                        .flatMap(member ->
                            groupService.removeMember(member.groupId(), member.id(), member.websocketId().toString()))
                    )
            )
            .assertNext(member -> assertThat(member.memberStatus()).isEqualTo(MemberStatus.LEFT))
            .verifyComplete();
    }

    @Test
    @DisplayName("Sets member exited date when removing a member")
    void setMemberExitedDateWhenRemovingMember() {
        final Instant testStartTime = Instant.now();
        StepVerifier.create(
                groupService.createGroup(GROUP, GROUP_DESCRIPTION, 10)
                    .flatMap(group ->
                        groupService.addMember(group.id(), USER, UUID.randomUUID().toString())
                        .flatMap(member ->
                            groupService.removeMember(member.groupId(), member.id(), member.websocketId().toString()))
                    )
            )
            .assertNext(member -> assertThat(member.exitedDate()).isBetween(testStartTime, Instant.now()))
            .verifyComplete();
    }

    @Test
    @DisplayName("Automatically set all member statuses of a group to non-active when disbanding a group")
    void automaticallySetMemberStatusToLeftWhenDisbandingGroup() {
        final Instant testStartTime = Instant.now();
        StepVerifier.create(
                groupService.createGroup(GROUP, GROUP_DESCRIPTION, 10)
                    .flatMap(group ->
                        Flux.range(1, 5)
                            .concatMap(i ->
                                groupService.addMember(group.id(), "user" + i, UUID.randomUUID().toString()))
                            .then(Mono.just(group))
                    )
                    .flatMap(group -> groupService.disbandGroup(group.id(), GroupStatus.DISBANDED))
                    .flatMap(group -> memberRepository.getMembersByGroup(group.id()).collectList())
            )
            .assertNext(members -> {
                assertThat(members).hasSize(5);
                assertThat(members).allSatisfy(member -> {
                    assertThat(member.version()).isEqualTo(2);
                    assertThat(member.memberStatus()).isEqualTo(MemberStatus.AUTO_LEFT);
                    assertThat(member.exitedDate()).isBetween(testStartTime, member.lastModifiedDate());
                });
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Does not allow a member to be added twice to a group")
    void doesNotAllowMemberToBeAddedTwiceToGroup() {
        StepVerifier.create(
                groupService.createGroup(GROUP, GROUP_DESCRIPTION, 10)
                    .flatMap(group -> groupService.addMember(group.id(), USER, UUID.randomUUID().toString())
                        .flatMap(member ->
                            groupService.addMember(
                                member.groupId(), "user1Alt", member.websocketId().toString()))
                    )
            )
            .expectError(UserAlreadyInGroupException.class)
            .verify();
    }

    @Test
    @DisplayName("Fails gracefully when trying to remove a member that does not exist")
    void failsGracefullyWhenRemovingMemberThatDoesNotExist() {
        StepVerifier.create(
                groupService.createGroup(GROUP, GROUP_DESCRIPTION, 10)
                    .flatMap(group ->
                        groupService.removeMember(group.id(), 1L, UUID.randomUUID().toString()))
            )
            .expectError(MemberNotFoundException.class)
            .verify();
    }

    @Test
    @DisplayName("Finds active member for user when user is in a group")
    void findsActiveMemberForUserWhenUserIsInGroup() {
        StepVerifier.create(
                groupService.createGroup(GROUP, GROUP_DESCRIPTION, 10)
                    .flatMap(group ->
                        groupService.addMember(group.id(), USER, UUID.randomUUID().toString())
                            .flatMap(member ->
                                groupService.findActiveMemberForUser(member.websocketId().toString()))
                    )
            )
            .assertNext(member -> assertThat(member.username()).isEqualTo(USER))
            .verifyComplete();
    }

    @Test
    @DisplayName("Returns nothing when trying to find active member for user when user is not in a group")
    void failsGracefullyWhenFindingActiveMemberForUserWhenUserIsNotInGroup() {
        StepVerifier.create(
                groupService.createGroup(GROUP, GROUP_DESCRIPTION, 10)
                    .flatMap(group ->
                        groupService.addMember(group.id(), USER, UUID.randomUUID().toString())
                            .flatMap(member ->
                                groupService.findActiveMemberForUser(UUID.randomUUID().toString()))
                    )
            )
            .verifyComplete();
    }
}
