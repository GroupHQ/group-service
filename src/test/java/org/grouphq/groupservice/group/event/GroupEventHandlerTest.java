package org.grouphq.groupservice.group.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.validation.Validator;
import java.util.Set;
import org.grouphq.groupservice.group.domain.groups.GroupEventService;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.domain.members.MemberEventService;
import org.grouphq.groupservice.group.event.daos.GroupCreateRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupJoinRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupLeaveRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupStatusRequestEvent;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Tag("UnitTest")
@ExtendWith(MockitoExtension.class)
class GroupEventHandlerTest {

    @Mock
    private GroupEventService groupEventService;

    @Mock
    private MemberEventService memberEventService;

    @Mock
    private Validator validator;

    @InjectMocks
    private GroupEventHandler groupEventHandler;

    @Test
    @DisplayName("Successfully joins a group")
    void dispatchGroupJoinSuccessfully() {
        final GroupJoinRequestEvent event = GroupTestUtility.generateGroupJoinRequestEvent();

        final Flux<GroupJoinRequestEvent> eventFlux = Flux.just(event);

        given(validator.validate(event))
            .willReturn(Set.of());

        given(memberEventService.joinGroup(event))
            .willReturn(Mono.empty());

        groupEventHandler.groupJoinRequests().accept(eventFlux);

        verify(validator).validate(event);
        verify(memberEventService).joinGroup(event);
        verify(memberEventService, never()).joinGroupFailed(eq(event), any());
    }

    @Test
    @DisplayName("Unsuccessfully joins a group")
    void dispatchGroupJoinUnsuccessfully() {
        final GroupJoinRequestEvent event = GroupTestUtility.generateGroupJoinRequestEvent();

        final Flux<GroupJoinRequestEvent> eventFlux = Flux.just(event);

        given(validator.validate(event))
            .willReturn(Set.of());

        given(memberEventService.joinGroup(event))
            .willReturn(Mono.error(new RuntimeException()));

        given(memberEventService.joinGroupFailed(eq(event), any(Throwable.class)))
            .willReturn(Mono.empty());

        groupEventHandler.groupJoinRequests().accept(eventFlux);

        verify(validator).validate(event);
        verify(memberEventService).joinGroup(event);
        verify(memberEventService).joinGroupFailed(eq(event), any(Throwable.class));
    }

    @Test
    @DisplayName("Successfully leaves a group")
    void dispatchGroupLeaveSuccessfully() {
        final GroupLeaveRequestEvent event = GroupTestUtility.generateGroupLeaveRequestEvent();

        final Flux<GroupLeaveRequestEvent> eventFlux = Flux.just(event);

        given(validator.validate(event))
            .willReturn(Set.of());

        given(memberEventService.removeMember(event))
            .willReturn(Mono.empty());

        groupEventHandler.groupLeaveRequests().accept(eventFlux);

        verify(validator).validate(event);
        verify(memberEventService).removeMember(event);
        verify(memberEventService, never()).removeMemberFailed(eq(event), any());
    }

    @Test
    @DisplayName("Unsuccessfully leaves a group")
    void dispatchGroupLeaveUnsuccessfully() {
        final GroupLeaveRequestEvent event = GroupTestUtility.generateGroupLeaveRequestEvent();

        final Flux<GroupLeaveRequestEvent> eventFlux = Flux.just(event);

        given(validator.validate(event))
            .willReturn(Set.of());

        given(memberEventService.removeMember(event))
            .willReturn(Mono.error(new RuntimeException()));

        groupEventHandler.groupLeaveRequests().accept(eventFlux);

        verify(validator).validate(event);
        verify(memberEventService).removeMember(event);
        verify(memberEventService).removeMemberFailed(eq(event), any(Throwable.class));
    }

    @Test
    @DisplayName("Successfully creates a group")
    void dispatchGroupCreateSuccessfully() {
        final GroupCreateRequestEvent event =
            GroupTestUtility.generateGroupCreateRequestEvent();

        final Flux<GroupCreateRequestEvent> eventFlux = Flux.just(event);

        given(validator.validate(event))
            .willReturn(Set.of());

        given(groupEventService.createGroup(event))
            .willReturn(Mono.empty());

        groupEventHandler.groupCreateRequests().accept(eventFlux);

        verify(validator).validate(event);
        verify(groupEventService).createGroup(event);
        verify(groupEventService, never()).createGroupFailed(any(), any());
    }

    @Test
    @DisplayName("Unsuccessfully creates a group")
    void dispatchGroupCreateUnsuccessfully() {
        final GroupCreateRequestEvent event =
            GroupTestUtility.generateGroupCreateRequestEvent();

        final Flux<GroupCreateRequestEvent> eventFlux = Flux.just(event);

        given(groupEventService.createGroup(event))
            .willReturn(Mono.error(new RuntimeException()));

        groupEventHandler.groupCreateRequests().accept(eventFlux);

        verify(groupEventService).createGroup(event);
        verify(groupEventService).createGroupFailed(eq(event), any(Throwable.class));
    }

    @Test
    @DisplayName("Successfully updates group status")
    void dispatchGroupUpdateStatusSuccessfully() {
        final GroupStatusRequestEvent event =
            GroupTestUtility.generateGroupStatusRequestEvent(1L, GroupStatus.ACTIVE);

        final Flux<GroupStatusRequestEvent> eventFlux = Flux.just(event);

        given(validator.validate(event))
            .willReturn(Set.of());

        given(groupEventService.updateGroupStatus(event))
            .willReturn(Mono.empty());

        groupEventHandler.groupStatusRequests().accept(eventFlux);

        verify(validator).validate(event);
        verify(groupEventService).updateGroupStatus(event);
        verify(groupEventService, never()).updateGroupStatusFailed(any(), any());
    }

    @Test
    @DisplayName("Unsuccessfully updates group status")
    void dispatchGroupUpdateStatusUnsuccessfully() {
        final GroupStatusRequestEvent event =
            GroupTestUtility.generateGroupStatusRequestEvent(1L, GroupStatus.ACTIVE);

        final Flux<GroupStatusRequestEvent> eventFlux = Flux.just(event);

        given(validator.validate(event))
            .willReturn(Set.of());

        given(groupEventService.updateGroupStatus(event))
            .willReturn(Mono.error(new RuntimeException()));

        groupEventHandler.groupStatusRequests().accept(eventFlux);

        verify(validator).validate(event);
        verify(groupEventService).updateGroupStatus(event);
        verify(groupEventService).updateGroupStatusFailed(eq(event), any(Throwable.class));
    }

}
