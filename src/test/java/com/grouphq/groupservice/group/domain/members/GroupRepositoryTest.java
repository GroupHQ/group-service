package com.grouphq.groupservice.group.domain.members;

import static org.assertj.core.api.Assertions.assertThat;

import com.grouphq.groupservice.config.DataConfig;
import com.grouphq.groupservice.group.domain.groups.Group;
import com.grouphq.groupservice.group.domain.groups.GroupRepository;
import com.grouphq.groupservice.group.domain.groups.GroupStatus;
import java.util.ArrayList;
import java.util.List;
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
    static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.4"));

    @Autowired
    private GroupRepository groupRepository;

    private static Group[] testGroups;

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
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
        ).verifyComplete();
    }

    @AfterEach
    void deleteRepositoryData() {
        StepVerifier.create(groupRepository.deleteAll())
            .verifyComplete();
    }

    @Test
    @DisplayName("Get all active groups from database")
    void retrievesOnlyActiveGroups() {
        final List<Group> groupsReturned = new ArrayList<>();

        StepVerifier.create(groupRepository.getAllGroups())
            .recordWith(() -> groupsReturned)
            .expectNextCount(3)
            .verifyComplete();

        assertThat(groupsReturned)
            .filteredOn(group -> group.status() == GroupStatus.ACTIVE)
            .hasSize(2);
    }

    @Test
    @DisplayName("Get all groups from database")
    void retrievesAllGroups() {
        final List<Group> groupsReturned = new ArrayList<>();

        StepVerifier.create(groupRepository.getAllGroups())
            .recordWith(() -> groupsReturned)
            .expectNextCount(3)
            .verifyComplete();

        assertThat(groupsReturned)
            .hasSize(3)
            .filteredOn(group -> group.status() == GroupStatus.ACTIVE)
            .hasSize(2);

        assertThat(groupsReturned)
            .filteredOn(group -> group.status() == GroupStatus.AUTO_DISBANDED)
            .hasSize(1);
    }
}
