package com.grouphq.groupservice.group.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.grouphq.groupservice.group.domain.groups.Group;
import com.grouphq.groupservice.group.domain.groups.GroupRepository;
import com.grouphq.groupservice.group.domain.groups.GroupService;
import com.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;

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

    boolean wasSubscribed;

    @BeforeEach
    public void setUp() {
        groupDemoLoader = new GroupDemoLoader(groupService, groupRepository,
            3, 5);
    }

    @Test
    @DisplayName("Generates group and saves a group to the database")
    void loaderTest() {
        final Group testGroup = Group.of("A", "B", 5, 1, GroupStatus.ACTIVE);
        final Mono<Group> customMono = new Mono<>() {
            @Override
            public void subscribe(@NotNull CoreSubscriber<? super Group> actual) {
                wasSubscribed = true;
                Mono.just(testGroup)
                    .subscribe(actual);
            }
        };

        given(groupService.generateGroup()).willReturn(testGroup);
        given(groupRepository.save(any(Group.class))).willReturn(customMono);

        groupDemoLoader.loadGroups();

        verify(groupService, times(3)).generateGroup();
        verify(groupRepository, times(3)).save(any(Group.class));
        assertThat(wasSubscribed).isTrue();
    }

    @Test
    @DisplayName("Updates active groups older than expiry time to auto-disbanded status")
    void expiryTest() {
        given(groupService.expireGroups()).willReturn(Mono.just(0));
        groupDemoLoader.expireGroups();
        verify(groupService, times(1)).expireGroups();
    }

}
