package com.grouphq.groupservice.group.domain.members;

import com.grouphq.groupservice.config.DataConfig;
import com.grouphq.groupservice.group.domain.groups.Group;
import com.grouphq.groupservice.group.domain.groups.GroupRepository;
import com.grouphq.groupservice.group.testutility.GroupTestUtility;
import java.time.Duration;
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
    static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.4"));

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    private MemberRepository memberRepository;

    private AtomicReference<Group> group;

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
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

        group = new AtomicReference<>(GroupTestUtility.generateFullGroupDetails());

        StepVerifier.create(groupRepository.save(group.get()))
            .consumeNextWith(group::set)
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Adds member to a group")
    void memberAddition() {
        final Member member = addMemberToGroup("User");

        StepVerifier.create(memberRepository.save(member))
            .expectNextMatches(memberSaved -> memberSaved.groupId().equals(group.get().id()))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Retrieves members belonging to a group")
    void memberRetrieval() {
        StepVerifier.create(memberRepository.getMembersByGroup(group.get().id()))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        addMemberToGroup("User");

        StepVerifier.create(memberRepository.getMembersByGroup(group.get().id()))
            .expectNextMatches(retrievedMember  -> "User".equals(retrievedMember.username()))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
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
}
