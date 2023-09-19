package org.grouphq.groupservice.group.demo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupRepository;
import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.event.daos.GroupStatusRequestEvent;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Tests GroupDemoLoader's method logic.
 */
@Tag("UnitTest")
@ExtendWith(MockitoExtension.class)
class GroupDemoLoaderTest {

    @Mock
    private GroupService groupService;

    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private GroupDemoLoader groupDemoLoader;

    @Test
    @DisplayName("Generates group and saves a group to the database")
    void loaderTest() {
        final Group mockGroup = mock(Group.class);

        given(groupService.generateGroup()).willReturn(mockGroup);

        final ArgumentMatcher<Flux<Group>> matcher = flux -> true;
        given(groupRepository.saveAll(argThat(matcher)))
            .willReturn(Flux.just(mockGroup, mockGroup, mockGroup));

        StepVerifier.create(groupDemoLoader.loadGroups(3, 1))
            .expectNextCount(3)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(groupService, times(3)).generateGroup();
        verify(groupRepository).saveAll(argThat(matcher));
    }

    @Test
    @DisplayName("Updates active groups past cutoff time to auto-disbanded status")
    void expiryTest() {
        final Instant cutoffTime = Instant.now().minus(1, ChronoUnit.HOURS);
        final Group group = GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE);

        given(groupService.getActiveGroupsPastCutoffDate(cutoffTime))
            .willReturn(Flux.just(group));

        given(groupService.updateGroupStatus(any(GroupStatusRequestEvent.class)))
            .willReturn(Mono.empty());

        StepVerifier.create(groupDemoLoader.expireGroups(cutoffTime))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(groupService).getActiveGroupsPastCutoffDate(cutoffTime);
        verify(groupService).updateGroupStatus(
            argThat(request -> request.getNewStatus() == GroupStatus.AUTO_DISBANDED));
    }

}
