package org.grouphq.groupservice.group.demo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.grouphq.groupservice.config.GroupProperties;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupEventService;
import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupCreateRequestEvent;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupStatusRequestEvent;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.function.Tuples;

/**
 * Tests GroupDemoLoader's method logic.
 */
@Tag("UnitTest")
@ExtendWith(MockitoExtension.class)
class GroupDemoLoaderTest {

    @Mock
    private GroupService groupService;

    @Mock
    private GroupGeneratorService groupGeneratorService;

    @Spy
    private final CharacterGeneratorService characterGeneratorService =
        new CharacterGeneratorService(new GroupProperties());

    @Mock
    private GroupEventService groupEventService;

    @InjectMocks
    private GroupDemoLoader groupDemoLoader;

    @Test
    @DisplayName("Generates group and saves a group to the database")
    void loaderTest() {
        final Group mockGroup = mock(Group.class);
        final CharacterEntity mockCharacterEntity = mock(CharacterEntity.class);

        given(groupGeneratorService.generateGroup(any(CharacterEntity.class)))
            .willReturn(Mono.just(Tuples.of(mockGroup, mockCharacterEntity)));

        given(groupEventService.createGroup(any(GroupCreateRequestEvent.class)))
            .willReturn(Mono.empty());

        StepVerifier.create(groupDemoLoader.loadGroups(3, 1))
            .expectComplete()
            .verify();

        verify(groupGeneratorService, times(3)).generateGroup(any(CharacterEntity.class));
        verify(groupEventService, times(3)).createGroup(any(GroupCreateRequestEvent.class));
    }

    @Test
    @DisplayName("Updates active groups past cutoff time to auto-disbanded status")
    void expiryTest() {
        final Instant cutoffTime = Instant.now().minus(1, ChronoUnit.HOURS);
        final Group group = GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE);

        given(groupService.findActiveGroupsCreatedBefore(cutoffTime))
            .willReturn(Flux.just(group));

        given(groupEventService.autoDisbandGroup(any(GroupStatusRequestEvent.class)))
            .willReturn(Mono.empty());

        StepVerifier.create(groupDemoLoader.expireGroupsCreatedBefore(cutoffTime))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(groupService).findActiveGroupsCreatedBefore(cutoffTime);
        verify(groupEventService).autoDisbandGroup(
            argThat(request -> request.getNewStatus() == GroupStatus.AUTO_DISBANDED));
    }

}
