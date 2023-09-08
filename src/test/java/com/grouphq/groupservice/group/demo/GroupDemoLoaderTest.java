package com.grouphq.groupservice.group.demo;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.grouphq.groupservice.group.domain.groups.Group;
import com.grouphq.groupservice.group.domain.groups.GroupRepository;
import com.grouphq.groupservice.group.domain.groups.GroupService;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
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

    private GroupDemoLoader groupDemoLoader;

    @BeforeEach
    public void setUp() {
        groupDemoLoader = new GroupDemoLoader(groupService, groupRepository,
            3, 5);
    }

    @Test
    @DisplayName("Generates group and saves a group to the database")
    void loaderTest() {
        final Group mockGroup = mock(Group.class);

        given(groupService.generateGroup()).willReturn(mockGroup);

        final ArgumentMatcher<Flux<Group>> matcher = flux -> true;
        given(groupRepository.saveAll(argThat(matcher)))
            .willReturn(Flux.just(mockGroup, mockGroup, mockGroup));

        StepVerifier.create(groupDemoLoader.loadGroups())
            .expectNextCount(3)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(groupService, times(3)).generateGroup();
        verify(groupRepository, times(1)).saveAll(argThat(matcher));
    }

    @Test
    @DisplayName("Updates active groups older than expiry time to auto-disbanded status")
    void expiryTest() {
        given(groupService.expireGroups()).willReturn(Mono.empty());

        StepVerifier.create(groupDemoLoader.expireGroups())
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(groupService, times(1)).expireGroups();
    }

}
