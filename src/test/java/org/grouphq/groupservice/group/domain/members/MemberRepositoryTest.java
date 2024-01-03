package org.grouphq.groupservice.group.domain.members;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.grouphq.groupservice.config.DataConfig;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.domain.groups.repository.GroupRepository;
import org.grouphq.groupservice.group.domain.members.repository.MemberRepository;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
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
import reactor.test.StepVerifier;

/**
 * Tests group membership.
 */
@DataR2dbcTest
@Import(DataConfig.class)
@Testcontainers
@Tag("IntegrationTest")
class MemberRepositoryTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.4"));

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MemberRepository memberRepository;
    
    private static final String USER = "User";

    @DynamicPropertySource
    private static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", MemberRepositoryTest::r2dbcUrl);
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
    @DisplayName("Get active members by group")
    void retrieveActiveMembersByGroup() {
        StepVerifier.create(
            groupRepository.save(GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE))
                .flatMapMany(group -> {
                    final List<Member> members = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        members.add(Member.of(USER + i, group.id()));
                    }
                    return memberRepository.saveAll(members)
                        .thenMany(memberRepository.getMembersByGroup(group.id()));
                })
        )
            .recordWith(ArrayList::new)
            .expectNextCount(3)
            .expectRecordedMatches(members -> members.stream()
                .allMatch(member -> member.memberStatus().equals(MemberStatus.ACTIVE)))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Allows member to leave their group")
    void updateActiveMember() {
        final Instant testStartTime = Instant.now();
        StepVerifier.create(
            groupRepository.save(GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE))
                .flatMap(group -> memberRepository.save(Member.of(USER, group.id())))
                .flatMap(savedMember -> memberRepository.removeMemberFromGroup(
                    savedMember.id(), savedMember.websocketId(), MemberStatus.LEFT)
                )
        )
            .assertNext(member -> {
                assertThat(member.memberStatus()).isEqualTo(MemberStatus.LEFT);
                assertThat(member.exitedDate()).isNotNull();
                assertThat(member.exitedDate()).isAfter(testStartTime);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Does not save a member if group to join is not active")
    void disallowNonActiveGroupJoining() {
        StepVerifier.create(
            groupRepository.save(GroupTestUtility.generateFullGroupDetails(GroupStatus.DISBANDED))
                .flatMap(group -> memberRepository.save(Member.of(USER, group.id())))
        )
            .expectErrorMatches(throwable -> throwable.getMessage().contains(
                "Cannot save member with group because the group is not active"))
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Does not save a member if group to join is full")
    void disallowFullGroupJoining() {
        StepVerifier.create(
            groupRepository.save(GroupTestUtility.generateFullGroupDetails(3, GroupStatus.ACTIVE))
                .flatMap(group -> {
                    final List<Member> members = new ArrayList<>();
                    for (int i = 0; i < group.maxGroupSize(); i++) {
                        members.add(Member.of(USER + i, group.id()));
                    }
                    return memberRepository.saveAll(members)
                        .then(memberRepository.save(Member.of(USER, group.id())));
                })
        )
            .expectErrorMatches(throwable -> throwable.getMessage().contains(
                "Group has reached its maximum size"))
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Does not update a member if member is not active")
    void disallowMemberUpdateWithMemberNonActive() {
        StepVerifier.create(
            groupRepository.save(GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE))
                .flatMap(group -> {
                    final Member member = Member.of(USER, group.id());
                    return memberRepository.save(member)
                        .flatMap(savedMember -> {
                            final Member memberWithChanges = savedMember.withStatus(MemberStatus.LEFT);
                            return memberRepository.save(memberWithChanges)
                                .flatMap(savedMemberLeft ->
                                    memberRepository.save(savedMemberLeft.withStatus(MemberStatus.ACTIVE)));
                        });
                })
        )
            .expectErrorMatches(throwable -> throwable.getMessage().contains(
                "Cannot update member because member status is not ACTIVE"))
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Retrieves members belonging to a group")
    void viewGroupMembers() {
        StepVerifier.create(
            groupRepository.save(GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE))
                .flatMapMany(group -> {
                    final List<Member> members = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        members.add(Member.of(USER + i, group.id()));
                    }
                    return memberRepository.saveAll(members)
                        .thenMany(memberRepository.getMembersByGroup(group.id()));
                })
        )
            .recordWith(ArrayList::new)
            .expectNextCount(3)
            .expectRecordedMatches(members -> members.stream()
                .allMatch(member -> member.memberStatus().equals(MemberStatus.ACTIVE)))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Updates exited date and version when removing member from group")
    void setsExitedDateWhenRemovingMemberFromGroup() {
        final Instant testStartTime = Instant.now();
        StepVerifier.create(
                groupRepository.save(GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE))
                    .flatMap(group -> {
                        final Member member = Member.of(USER, group.id());
                        return memberRepository.save(member)
                            .flatMap(savedMember -> memberRepository.removeMemberFromGroup(
                                    savedMember.id(), savedMember.websocketId(), MemberStatus.LEFT)
                            );
                    })
            )
            .assertNext(member -> {
                assertThat(member.version()).isEqualTo(2);
                assertThat(member.exitedDate()).isBetween(testStartTime, member.lastModifiedDate());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Does not update exited date when trying to remove a member keeping their status as ACTIVE")
    void keepMemberIntegrityWhenTryingToIncorrectlyRemoveMember() {
        StepVerifier.create(
                groupRepository.save(GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE))
                    .flatMap(group -> {
                        final Member member = Member.of(USER, group.id());
                        return memberRepository.save(member)
                            .flatMap(savedMember -> memberRepository.removeMemberFromGroup(
                                    savedMember.id(), savedMember.websocketId(), MemberStatus.ACTIVE)
                            );
                    })
            )
            .expectError(IllegalArgumentException.class)
            .verify();
    }

    @Test
    @DisplayName("Return member by group and websocketId")
    void returnMemberByGroupAndSocketId() {
        StepVerifier.create(
            groupRepository.save(GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE))
                .flatMap(group -> {
                    final Member member = Member.of(USER, group.id());
                    return memberRepository.save(member)
                        .flatMap(savedMember -> memberRepository.findMemberByIdAndWebsocketId(
                            savedMember.id(), savedMember.websocketId()));
                })
        )
            .expectNextCount(1)
            .verifyComplete();
    }

    @Test
    @DisplayName("Does not update member if websocketId does not match")
    void doesNotRemoveMemberUsingIncorrectWebsocketId() {
        StepVerifier.create(
                groupRepository.save(GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE))
                    .flatMap(group ->
                        memberRepository.save(Member.of(UUID.randomUUID(), USER, group.id())))
                    .flatMap(savedMember ->
                        memberRepository.removeMemberFromGroup(savedMember.id(), UUID.randomUUID(), MemberStatus.LEFT))
            )
            .expectNextCount(0)
            .verifyComplete();
    }

    @Test
    @DisplayName("Auto disbands active members and updates exited date and version")
    void autoDisbandActiveMembers() {
        final Instant testStartTime = Instant.now();
        StepVerifier.create(
                groupRepository.save(GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE))
                    .flatMapMany(group -> {
                        final List<Member> members = new ArrayList<>();
                        for (int i = 0; i < 3; i++) {
                            members.add(Member.of(USER + i, group.id()));
                        }
                        return memberRepository.saveAll(members)
                            .thenMany(memberRepository.autoDisbandActiveMembers(group.id()).collectList());
                    })
            )
            .assertNext(members -> assertThat(members).allSatisfy(member -> {
                assertThat(member.version()).isEqualTo(2);
                assertThat(member.memberStatus()).isEqualTo(MemberStatus.AUTO_LEFT);
                assertThat(member.exitedDate()).isBetween(testStartTime, member.lastModifiedDate());
            }))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Returns member by websocketId and member status")
    void returnMemberByWebsocketIdAndMemberStatus() {
        StepVerifier.create(
                groupRepository.save(GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE))
                    .flatMap(group -> {
                        final Member member = Member.of(USER, group.id());
                        return memberRepository.save(member)
                            .flatMap(savedMember -> memberRepository.findMemberByWebsocketIdAndMemberStatus(
                                savedMember.websocketId(), savedMember.memberStatus()));
                    })
            )
            .assertNext(member -> {
                assertThat(member.username()).isEqualTo(USER);
                assertThat(member.memberStatus()).isEqualTo(MemberStatus.ACTIVE);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Returns nothing if no member found by websocketId and member status")
    void returnNothingIfNoMemberFoundByWebsocketIdAndMemberStatus() {
        StepVerifier.create(
                groupRepository.save(GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE))
                    .flatMap(group -> {
                        final Member member = Member.of(USER, group.id());
                        return memberRepository.save(member)
                            .flatMap(savedMember -> memberRepository.removeMemberFromGroup(
                                savedMember.id(), savedMember.websocketId(), MemberStatus.LEFT))
                            .flatMap(leftMember -> memberRepository.findMemberByWebsocketIdAndMemberStatus(
                                leftMember.websocketId(), MemberStatus.ACTIVE));
                    })
            )
            .verifyComplete();
    }
}
