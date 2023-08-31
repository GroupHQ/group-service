package com.grouphq.groupservice.group.domain.groups;

import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@Tag("UnitTest")
@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private GroupService groupService;

    private static Group[] testGroups;

    @BeforeAll
    static void setUp() {
        final String groupOwner = "system";

        testGroups = new Group[] {
            new Group(123_456L, "Example Title", "Example Description", 10,
                1, GroupStatus.ACTIVE, Instant.now(),
                Instant.now().minus(20, ChronoUnit.MINUTES),
                Instant.now().minus(5, ChronoUnit.MINUTES),
                groupOwner, groupOwner, 3),
            new Group(7890L, "Example Title", "Example Description", 5,
                2, GroupStatus.ACTIVE, Instant.now(),
                Instant.now().minus(12, ChronoUnit.MINUTES),
                Instant.now().minus(1, ChronoUnit.MINUTES),
                groupOwner, groupOwner, 2)
        };
    }

    @Test
    @DisplayName("Gets (active) groups")
    void retrievesOnlyActiveGroups() {
        final Flux<Group> mockedGroups = Flux.just(testGroups);

        given(groupRepository.findGroupsByStatus(GroupStatus.ACTIVE)).willReturn(mockedGroups);

        final Flux<Group> retrievedGroups = groupService.getGroups();
        StepVerifier.create(retrievedGroups)
            .expectNext(testGroups[0], testGroups[1])
            .verifyComplete();
    }
}
