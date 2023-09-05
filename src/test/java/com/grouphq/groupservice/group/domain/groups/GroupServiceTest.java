package com.grouphq.groupservice.group.domain.groups;

import static com.grouphq.groupservice.group.testutility.GroupTestUtility.generateFullGroupDetails;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Tag("UnitTest")
@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    private GroupService groupService;

    @BeforeEach
    public void setUp() {
        groupService = new GroupService(groupRepository, 30);
    }

    @Test
    @DisplayName("Gets (active) groups")
    void retrievesOnlyActiveGroups() {
        final Group[] testGroups = {
            generateFullGroupDetails(Instant.now()),
            generateFullGroupDetails(Instant.now())
        };

        final Flux<Group> mockedGroups = Flux.just(testGroups);

        given(groupRepository.findGroupsByStatus(GroupStatus.ACTIVE)).willReturn(mockedGroups);

        final Flux<Group> retrievedGroups = groupService.getGroups();

        StepVerifier.create(retrievedGroups)
            .expectNextMatches(group -> matchGroup(group, testGroups[0], 0))
            .expectNextMatches(group -> matchGroup(group, testGroups[1], 1))
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Sends expire request to repository interface")
    void expireGroups() {
        final Mono<Integer> mono = groupService.expireGroups();
        assertThat(mono).isNull();
        verify(groupRepository)
            .expireGroupsPastExpiryDate(any(Instant.class), any(GroupStatus.class));
    }

    private boolean matchGroup(Group actual, Group expected, int index) {
        if (actual.equals(expected)) {
            return true;
        } else {
            throw new AssertionError(
                String.format(
                    "Test group %d should equal returned group\nExpected: %s\nActual:   %s",
                    index, expected, actual)
            );
        }
    }
}
