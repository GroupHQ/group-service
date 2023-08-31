package com.grouphq.groupservice.cucumber.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.grouphq.groupservice.config.DataConfig;
import com.grouphq.groupservice.config.SecurityConfig;
import com.grouphq.groupservice.group.domain.groups.Group;
import com.grouphq.groupservice.group.domain.groups.GroupRepository;
import com.grouphq.groupservice.group.domain.groups.GroupStatus;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Import({DataConfig.class, SecurityConfig.class})
@Testcontainers
@Tag("AcceptanceTest")
public class ActiveGroupsPolicy {

    @Container
    static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.4"));

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private WebTestClient webTestClient;

    private WebTestClient.ListBodySpec<Group> groupResponse;

    @DynamicPropertySource
    static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", ActiveGroupsPolicy::r2dbcUrl);
        registry.add("spring.r2dbc.username", POSTGRESQL_CONTAINER::getUsername);
        registry.add("spring.r2dbc.password", POSTGRESQL_CONTAINER::getPassword);
        registry.add("spring.flyway.url", POSTGRESQL_CONTAINER::getJdbcUrl);
    }

    private static String r2dbcUrl() {
        return String.format("r2dbc:postgresql://%s:%s/%s", POSTGRESQL_CONTAINER.getHost(),
            POSTGRESQL_CONTAINER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
            POSTGRESQL_CONTAINER.getDatabaseName());
    }

    @Given("there are active groups")
    public void thereAreActiveGroups() {
        final Group[] groups = {
            Group.of("Example Title", "Example Description", 10,
                1, GroupStatus.ACTIVE),
            Group.of("Example Title", "Example Description", 5,
                2, GroupStatus.ACTIVE),
            Group.of("Example Title", "Example Description", 5,
                2, GroupStatus.AUTO_DISBANDED)
        };

        StepVerifier.create(
            Mono.when(
                groupRepository.save(groups[0]),
                groupRepository.save(groups[1]),
                groupRepository.save(groups[2])
            )
        ).verifyComplete();
    }

    @When("I request groups")
    public void iRequestGroups() {
        groupResponse = webTestClient
            .get()
            .uri("/groups")
            .exchange()
            .expectStatus().is2xxSuccessful()
            .expectBodyList(Group.class);
    }

    @Then("I should be given a list of active groups")
    public void iShouldBeGivenAListOfActiveGroups() {
        groupResponse.value(groups -> {
            assertThat(groups).isNotEmpty();
            assertThat(groups).allMatch(group ->
                    group.status().equals(GroupStatus.ACTIVE),
                "All groups received should be active");
        });
    }
}
