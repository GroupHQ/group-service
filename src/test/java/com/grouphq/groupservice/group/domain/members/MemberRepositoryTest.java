package com.grouphq.groupservice.group.domain.members;

import static org.assertj.core.api.Assertions.assertThat;

import com.grouphq.groupservice.config.DataConfig;
import com.grouphq.groupservice.group.domain.groups.Group;
import com.grouphq.groupservice.group.domain.groups.GroupRepository;
import com.grouphq.groupservice.group.domain.groups.GroupStatus;
import com.grouphq.groupservice.group.testutility.GroupTestUtility;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
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
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Tests group membership.
 */
@DataR2dbcTest
@Import(DataConfig.class)
@Testcontainers
@Tag("IntegrationTest")
class MemberRepositoryTest {

    private static final String USERNAME = "User";
    private static final String OWNER = "system";

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.4"));

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private MemberRepository memberRepository;

    private AtomicReference<Group> group;

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

    @BeforeEach
    void addGroupToDatabase() {
        StepVerifier.create(memberRepository.deleteAll())
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        StepVerifier.create(groupRepository.deleteAll())
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        group = new AtomicReference<>(GroupTestUtility
            .generateFullGroupDetails(GroupStatus.ACTIVE));

        StepVerifier.create(groupRepository.save(group.get()))
            .consumeNextWith(group::set)
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }
    
    @Test
    @DisplayName("Get active members by group")
    void retrieveActiveMembersByGroup() {
        final Member[] members = {
            Member.of("User1", group.get().id()),
            Member.of("User2", group.get().id()),
            Member.of("User3", group.get().id()),
        };

        StepVerifier.create(memberRepository.saveAll(Flux.just(members)))
            .expectNextCount(3)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        StepVerifier.create(memberRepository.getActiveMembersByGroup(group.get().id()))
            .expectNextCount(3)
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Adds member to a group and increment group size")
    void joinGroup() {
        final Member member = addMemberToGroup(USERNAME);

        final int groupSizeBeforeJoining = group.get().currentGroupSize();

        StepVerifier.create(memberRepository.save(member))
            .expectNextMatches(memberSaved -> memberSaved.groupId().equals(group.get().id()))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        StepVerifier.create(groupRepository.findById(group.get().id()))
            .expectNextMatches(upToDateGroup ->
                groupSizeBeforeJoining + 1 == upToDateGroup.currentGroupSize())
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Allows member updates for active members who are part of active groups")
    void updateActiveMember() {
        final AtomicReference<Member> member = new AtomicReference<>(addMemberToGroup(USERNAME));

        StepVerifier.create(memberRepository.save(member.get()))
            .consumeNextWith(member::set)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        final Member memberWithChanges = new Member(
            member.get().id(), "New Username", member.get().groupId(), member.get().memberStatus(),
            member.get().joinedDate(), member.get().exitedDate(), member.get().createdDate(),
            member.get().lastModifiedDate(), member.get().createdBy(),
            member.get().lastModifiedBy(), member.get().version()
        );

        StepVerifier.create(memberRepository.save(memberWithChanges))
            .expectNextMatches(savedMember ->
                savedMember.username().equals(memberWithChanges.username()))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Does not save a member if group to join is not active")
    void disallowNonActiveGroupJoining() {
        final Group group = new Group(
            1234L, "Title", "Desc", 10, 5,
            GroupStatus.AUTO_DISBANDED, Instant.now(), Instant.now(), Instant.now(),
            OWNER, OWNER, 0
        );

        saveGroup(group);

        final Member member = Member.of(USERNAME, group.id());

        StepVerifier.create(memberRepository.save(member))
            .expectErrorMatches(throwable -> throwable.getMessage().contains(
                "Cannot save member with group because the group is not active"))
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Does not save a member if group to join is full")
    void disallowFullGroupJoining() {
        final Group group = new Group(
            1234L, "Title", "Desc", 10, 10,
            GroupStatus.ACTIVE, Instant.now(), Instant.now(), Instant.now(),
            OWNER, OWNER, 0
        );

        saveGroup(group);

        final Member member = Member.of(USERNAME, group.id());

        StepVerifier.create(memberRepository.save(member))
            .expectErrorMatches(throwable -> throwable.getMessage().contains(
                "Cannot save member with group because the group is full"))
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Does not update a member if member is not active")
    void disallowMemberUpdateWithMemberNonActive() {
        final AtomicReference<Member> member = new AtomicReference<>(addMemberToGroup(USERNAME));

        final Member notActiveMember = new Member(
            member.get().id(), member.get().username(), member.get().groupId(), MemberStatus.LEFT,
            member.get().joinedDate(), member.get().exitedDate(), member.get().createdDate(),
            member.get().lastModifiedDate(), member.get().createdBy(),
            member.get().lastModifiedBy(), member.get().version()
        );

        StepVerifier.create(memberRepository.save(notActiveMember))
            .consumeNextWith(member::set)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        final Member memberWithChanges = new Member(
            member.get().id(), member.get().username(), member.get().groupId(), MemberStatus.ACTIVE,
            member.get().joinedDate(), member.get().exitedDate(), member.get().createdDate(),
            member.get().lastModifiedDate(), member.get().createdBy(),
            member.get().lastModifiedBy(), member.get().version()
        );

        StepVerifier.create(memberRepository.save(memberWithChanges))
            .expectErrorMatches(throwable -> throwable.getMessage().contains(
                "Cannot update member because member status is not ACTIVE"))
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Retrieves members belonging to a group")
    void viewGroupMembers() {
        StepVerifier.create(memberRepository.getMembersByGroup(group.get().id()))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        addMemberToGroup(USERNAME);

        StepVerifier.create(memberRepository.getMembersByGroup(group.get().id()))
            .expectNextMatches(retrievedMember  -> USERNAME.equals(retrievedMember.username()))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Remove member from group and sync data")
    void removeMemberFromGroupAndSyncData() {
        final Member memberOfGroup = addMemberToGroup(USERNAME);

        StepVerifier.create(groupRepository.findById(group.get().id()))
            .consumeNextWith(group::set)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        final int groupSizeBeforeRemoving = group.get().currentGroupSize();

        StepVerifier.create(memberRepository.removeMemberFromGroup(memberOfGroup.id()))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        StepVerifier.create(memberRepository.getActiveMembersByGroup(group.get().id()))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        StepVerifier.create(groupRepository.findById(group.get().id()))
            .expectNextMatches(upToDateGroup ->
                groupSizeBeforeRemoving - 1 == upToDateGroup.currentGroupSize())
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        StepVerifier.create(memberRepository.findById(memberOfGroup.id()))
            .consumeNextWith(member -> {
                assertThat(member.exitedDate()).isNotNull();
                assertThat(member.memberStatus()).isEqualTo(MemberStatus.LEFT);
                assertThat(member.exitedDate()).isAfter(Instant.now().minusSeconds(1));
            })
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Set members' status to AUTO_LEFT if group is disbanded and sync data")
    void setMembersStatusToAutoLeftIfGroupIsDisbandedAndSyncData() {
        addMemberToGroup("Member 1");
        addMemberToGroup("Member 2");
        addMemberToGroup("Member 3");

        StepVerifier.create(groupRepository.findById(group.get().id()))
            .consumeNextWith(group::set)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        final int currentGroupSizeBeforeDisbanding = group.get().currentGroupSize();

        StepVerifier.create(groupRepository.save(new Group(
                group.get().id(), group.get().title(), group.get().description(),
                group.get().maxGroupSize(), group.get().currentGroupSize(), GroupStatus.DISBANDED,
                group.get().lastActive(), group.get().createdDate(), group.get().lastModifiedDate(),
                group.get().createdBy(), group.get().lastModifiedBy(), group.get().version())))
            .consumeNextWith(group::set)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        StepVerifier.create(memberRepository.getMembersByGroup(group.get().id()))
            .consumeNextWith(member -> {
                assertThat(member.memberStatus()).isEqualTo(MemberStatus.AUTO_LEFT);
                assertThat(member.exitedDate()).isAfter(Instant.now().minusSeconds(1));
            })
            .consumeNextWith(member -> {
                assertThat(member.memberStatus()).isEqualTo(MemberStatus.AUTO_LEFT);
                assertThat(member.exitedDate()).isAfter(Instant.now().minusSeconds(1));
            })
            .consumeNextWith(member -> {
                assertThat(member.memberStatus()).isEqualTo(MemberStatus.AUTO_LEFT);
                assertThat(member.exitedDate()).isAfter(Instant.now().minusSeconds(1));
            })
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        StepVerifier.create(groupRepository.findById(group.get().id()))
            .consumeNextWith(group::set)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        assertThat(group.get().currentGroupSize())
            .isEqualTo(currentGroupSizeBeforeDisbanding - 3);
    }

    private Member addMemberToGroup(String username) {
        final Member member = Member.of(username, group.get().id());
        final AtomicReference<Member> savedMember = new AtomicReference<>();

        StepVerifier.create(memberRepository.save(member))
            .consumeNextWith(savedMember::set)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        return savedMember.get();
    }

    private void saveGroup(Group group) {
        StepVerifier.create(groupRepository.save(group))
            .expectNextMatches(groupSaved -> groupSaved.id().equals(group.id()))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }
}
