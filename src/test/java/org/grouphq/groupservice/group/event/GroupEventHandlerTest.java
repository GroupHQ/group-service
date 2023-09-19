package org.grouphq.groupservice.group.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.domain.members.MemberService;
import org.grouphq.groupservice.group.event.daos.GroupCreateRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupJoinRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupLeaveRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupStatusRequestEvent;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
import jakarta.validation.Validator;
import java.util.Set;
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
    private GroupService groupService;

    @Mock
    private MemberService memberService;

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

        given(memberService.joinGroup(event))
            .willReturn(Mono.empty());

        groupEventHandler.handleGroupJoinRequests().accept(eventFlux);

        verify(validator).validate(event);
        verify(memberService).joinGroup(event);
        verify(memberService, never()).joinGroupFailed(eq(event), any());
    }

    @Test
    @DisplayName("Unsuccessfully joins a group")
    void dispatchGroupJoinUnsuccessfully() {
        final GroupJoinRequestEvent event = GroupTestUtility.generateGroupJoinRequestEvent();

        final Flux<GroupJoinRequestEvent> eventFlux = Flux.just(event);

        given(validator.validate(event))
            .willReturn(Set.of());

        given(memberService.joinGroup(event))
            .willReturn(Mono.error(new RuntimeException()));

        given(memberService.joinGroupFailed(eq(event), any(Throwable.class)))
            .willReturn(Mono.empty());

        groupEventHandler.handleGroupJoinRequests().accept(eventFlux);

        verify(validator).validate(event);
        verify(memberService).joinGroup(event);
        verify(memberService).joinGroupFailed(eq(event), any(Throwable.class));
    }

    @Test
    @DisplayName("Successfully leaves a group")
    void dispatchGroupLeaveSuccessfully() {
        final GroupLeaveRequestEvent event = GroupTestUtility.generateGroupLeaveRequestEvent();

        final Flux<GroupLeaveRequestEvent> eventFlux = Flux.just(event);

        given(validator.validate(event))
            .willReturn(Set.of());

        given(memberService.removeMember(event))
            .willReturn(Mono.empty());

        groupEventHandler.handleGroupLeaveRequests().accept(eventFlux);

        verify(validator).validate(event);
        verify(memberService).removeMember(event);
        verify(memberService, never()).removeMemberFailed(eq(event), any());
    }

    @Test
    @DisplayName("Unsuccessfully leaves a group")
    void dispatchGroupLeaveUnsuccessfully() {
        final GroupLeaveRequestEvent event = GroupTestUtility.generateGroupLeaveRequestEvent();

        final Flux<GroupLeaveRequestEvent> eventFlux = Flux.just(event);

        given(validator.validate(event))
            .willReturn(Set.of());

        given(memberService.removeMember(event))
            .willReturn(Mono.error(new RuntimeException()));

        groupEventHandler.handleGroupLeaveRequests().accept(eventFlux);

        verify(validator).validate(event);
        verify(memberService).removeMember(event);
        verify(memberService).removeMemberFailed(eq(event), any(Throwable.class));
    }

    @Test
    @DisplayName("Successfully creates a group")
    void dispatchGroupCreateSuccessfully() {
        final GroupCreateRequestEvent event =
            GroupTestUtility.generateGroupCreateRequestEvent();

        final Flux<GroupCreateRequestEvent> eventFlux = Flux.just(event);

        given(validator.validate(event))
            .willReturn(Set.of());

        given(groupService.createGroup(event))
            .willReturn(Mono.empty());

        groupEventHandler.handleGroupCreateRequests().accept(eventFlux);

        verify(validator).validate(event);
        verify(groupService).createGroup(event);
        verify(groupService, never()).createGroupFailed(any(), any());
    }

    @Test
    @DisplayName("Unsuccessfully creates a group")
    void dispatchGroupCreateUnsuccessfully() {
        final GroupCreateRequestEvent event =
            GroupTestUtility.generateGroupCreateRequestEvent();

        final Flux<GroupCreateRequestEvent> eventFlux = Flux.just(event);

        given(groupService.createGroup(event))
            .willReturn(Mono.error(new RuntimeException()));

        groupEventHandler.handleGroupCreateRequests().accept(eventFlux);

        verify(groupService).createGroup(event);
        verify(groupService).createGroupFailed(eq(event), any(Throwable.class));
    }

    @Test
    @DisplayName("Successfully updates group status")
    void dispatchGroupUpdateStatusSuccessfully() {
        final GroupStatusRequestEvent event =
            GroupTestUtility.generateGroupStatusRequestEvent(1L, GroupStatus.ACTIVE);

        final Flux<GroupStatusRequestEvent> eventFlux = Flux.just(event);

        given(validator.validate(event))
            .willReturn(Set.of());

        given(groupService.updateGroupStatus(event))
            .willReturn(Mono.empty());

        groupEventHandler.handleGroupStatusRequests().accept(eventFlux);

        verify(validator).validate(event);
        verify(groupService).updateGroupStatus(event);
        verify(groupService, never()).updateGroupStatusFailed(any(), any());
    }

    @Test
    @DisplayName("Unsuccessfully updates group status")
    void dispatchGroupUpdateStatusUnsuccessfully() {
        final GroupStatusRequestEvent event =
            GroupTestUtility.generateGroupStatusRequestEvent(1L, GroupStatus.ACTIVE);

        final Flux<GroupStatusRequestEvent> eventFlux = Flux.just(event);

        given(validator.validate(event))
            .willReturn(Set.of());

        given(groupService.updateGroupStatus(event))
            .willReturn(Mono.error(new RuntimeException()));

        groupEventHandler.handleGroupStatusRequests().accept(eventFlux);

        verify(validator).validate(event);
        verify(groupService).updateGroupStatus(event);
        verify(groupService).updateGroupStatusFailed(eq(event), any(Throwable.class));
    }

}
