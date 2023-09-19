package com.grouphq.groupservice.cucumber.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.grouphq.groupservice.group.domain.groups.Group;
import com.grouphq.groupservice.group.domain.groups.GroupRepository;
import com.grouphq.groupservice.group.domain.groups.GroupStatus;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest
@AutoConfigureWebTestClient
public class ActiveGroupsPolicy {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private WebTestClient webTestClient;

    private WebTestClient.ListBodySpec<Group> groupResponse;

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
            )
            .expectComplete()
            .verify(Duration.ofSeconds(1));
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

    @Given("any time")
    public void anyTime() {
        // such as now
    }

    @Then("I should be given a list of at least {int} active groups")
    public void iShouldBeGivenAListOfAtLeastActiveGroups(int activeGroupsNeeded) {
        final List<Group> groups = groupRepository.getAllGroups().collectList().block();

        assertThat(groups)
            .filteredOn(group -> group.status().equals(GroupStatus.ACTIVE))
            .hasSizeGreaterThanOrEqualTo(activeGroupsNeeded);
    }
}
