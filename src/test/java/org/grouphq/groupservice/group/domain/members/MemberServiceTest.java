package org.grouphq.groupservice.group.domain.members;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.grouphq.groupservice.group.domain.exceptions.EventAlreadyPublishedException;
import org.grouphq.groupservice.group.domain.exceptions.ExceptionMapper;
import org.grouphq.groupservice.group.domain.exceptions.GroupDoesNotExistException;
import org.grouphq.groupservice.group.domain.exceptions.GroupIsFullException;
import org.grouphq.groupservice.group.domain.exceptions.GroupNotActiveException;
import org.grouphq.groupservice.group.domain.exceptions.MemberNotActiveException;
import org.grouphq.groupservice.group.domain.exceptions.MemberNotFoundException;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.domain.outbox.ErrorData;
import org.grouphq.groupservice.group.domain.outbox.OutboxEvent;
import org.grouphq.groupservice.group.domain.outbox.OutboxService;
import org.grouphq.groupservice.group.domain.outbox.enums.AggregateType;
import org.grouphq.groupservice.group.domain.outbox.enums.EventStatus;
import org.grouphq.groupservice.group.domain.outbox.enums.EventType;
import org.grouphq.groupservice.group.event.daos.GroupJoinRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupLeaveRequestEvent;
import org.grouphq.groupservice.group.event.daos.RequestEvent;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Tag("UnitTest")
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final String USERNAME = "User";
    private static final String CANNOT_JOIN_GROUP = "Cannot join group";

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private GroupService groupService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private ExceptionMapper exceptionMapper;

    private MemberService memberService;

    private ObjectMapper objectMapper;

    private Group group;

    @BeforeEach
    void setUp() {
        group = GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        memberService = new MemberService(memberRepository, groupService,
                outboxService, exceptionMapper
        );
    }

    @Test
    @DisplayName("Gets members from a group")
    void viewGroupMembers() {
        final Member[] members = {
            Member.of("User1", group.id()),
            Member.of("User2", group.id()),
            Member.of("User3", group.id())
        };

        given(memberRepository.getActiveMembersByGroup(group.id())).willReturn(Flux.just(members));

        StepVerifier.create(memberService.getActiveMembers(group.id()))
            .expectNext(members[0], members[1], members[2])
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(memberRepository).getActiveMembersByGroup(group.id());
    }

    @Test
    @DisplayName("Process group join request successfully (adds a member to a group)")
    void processGroupJoin() throws JsonProcessingException {
        final GroupJoinRequestEvent event =
            GroupTestUtility.generateGroupJoinRequestEvent(group.id());

        final Member member = GroupTestUtility.generateFullMemberDetails(USERNAME, group.id());

        final OutboxEvent outboxEvent = OutboxEvent.of(
            event.getEventId(), event.getAggregateId(), AggregateType.GROUP,
            EventType.MEMBER_JOINED, objectMapper.writeValueAsString(member),
            EventStatus.SUCCESSFUL, event.getWebsocketId());

        given(outboxService.errorIfEventPublished(event))
            .willReturn(Mono.just(event));

        given(outboxService.createGroupJoinSuccessfulEvent(
            any(GroupJoinRequestEvent.class), any(Member.class)))
            .willReturn(Mono.just(outboxEvent));

        given(groupService.findById(group.id()))
            .willReturn(Mono.just(group));

        given(memberRepository.save(any(Member.class)))
            .willReturn(Mono.just(member));

        given(outboxService.saveOutboxEvent(any(OutboxEvent.class)))
            .willReturn(Mono.empty());

        StepVerifier.create(memberService.joinGroup(event))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(outboxService).errorIfEventPublished(event);
        verify(groupService).findById(group.id());
        verify(memberRepository).save(any(Member.class));
        verify(outboxService).createGroupJoinSuccessfulEvent(eq(event), any(Member.class));
        verify(outboxService).saveOutboxEvent(outboxEvent);
    }

    @Test
    @DisplayName("Process group join failure events successfully")
    void processJoinGroupFailed() throws JsonProcessingException {
        final GroupJoinRequestEvent event = GroupTestUtility.generateGroupJoinRequestEvent();

        final var failure = new GroupNotActiveException(CANNOT_JOIN_GROUP);

        final OutboxEvent outboxEvent = OutboxEvent.of(
            event.getEventId(), event.getAggregateId(),
            AggregateType.GROUP, EventType.MEMBER_JOINED,
            objectMapper.writeValueAsString(new ErrorData(failure.getMessage())),
            EventStatus.FAILED, event.getWebsocketId());

        given(outboxService.errorIfEventPublished(event))
            .willReturn(Mono.just(event));

        given(outboxService.createGroupJoinFailedEvent(any(
            GroupJoinRequestEvent.class), any(Throwable.class)))
            .willReturn(Mono.just(outboxEvent));

        given(outboxService.saveOutboxEvent(any(OutboxEvent.class)))
            .willReturn(Mono.empty());

        StepVerifier.create(memberService.joinGroupFailed(event, failure))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(outboxService).errorIfEventPublished(event);
        verify(outboxService).createGroupJoinFailedEvent(eq(event), any(Throwable.class));
        verifyNoInteractions(exceptionMapper);
        verify(outboxService).saveOutboxEvent(outboxEvent);
    }

    @Test
    @DisplayName("Does not create a member if group to join is not active")
    void disallowNonActiveGroupJoining() {
        final Group inactiveGroup = GroupTestUtility
            .generateFullGroupDetails(GroupStatus.AUTO_DISBANDED);

        final GroupJoinRequestEvent event =
            GroupTestUtility.generateGroupJoinRequestEvent(inactiveGroup.id());

        given(outboxService.errorIfEventPublished(event))
            .willReturn(Mono.just(event));

        given(groupService.findById(inactiveGroup.id())).willReturn(Mono.just(inactiveGroup));

        given(memberRepository.save(any(Member.class))).willThrow(
            new DataAccessResourceFailureException(
                "Cannot update member because member status is not ACTIVE")
        );

        given(exceptionMapper.getBusinessException(any(Throwable.class)))
            .willReturn(new MemberNotActiveException(CANNOT_JOIN_GROUP));

        StepVerifier.create(memberService.joinGroup(event))
            .expectErrorMatches(throwable -> throwable instanceof MemberNotActiveException)
            .verify(Duration.ofSeconds(1));

        verify(outboxService).errorIfEventPublished(event);
        verify(groupService).findById(inactiveGroup.id());
        verify(memberRepository).save(any(Member.class));
        verify(exceptionMapper).getBusinessException(any(Throwable.class));
        verify(outboxService, never())
            .createGroupJoinSuccessfulEvent(any(GroupJoinRequestEvent.class), any(Member.class));
        verify(outboxService, never()).saveOutboxEvent(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Does not create a member if group to join is full")
    void disallowFullGroupJoining() {
        final Group fullGroup = GroupTestUtility
            .generateFullGroupDetails(10, 10, GroupStatus.ACTIVE);

        final GroupJoinRequestEvent event =
            GroupTestUtility.generateGroupJoinRequestEvent(fullGroup.id());

        given(outboxService.errorIfEventPublished(event))
            .willReturn(Mono.just(event));

        given(groupService.findById(fullGroup.id())).willReturn(Mono.just(fullGroup));

        given(memberRepository.save(any(Member.class))).willThrow(
            new DataAccessResourceFailureException(
                "Cannot save member with group because the group is full")
        );

        given(exceptionMapper.getBusinessException(any(Throwable.class)))
            .willReturn(new GroupIsFullException(CANNOT_JOIN_GROUP));

        StepVerifier.create(memberService.joinGroup(event))
            .expectErrorMatches(throwable -> throwable instanceof GroupIsFullException)
            .verify(Duration.ofSeconds(1));

        verify(outboxService).errorIfEventPublished(event);
        verify(groupService).findById(fullGroup.id());
        verify(memberRepository).save(any(Member.class));
        verify(exceptionMapper).getBusinessException(any(Throwable.class));
        verify(outboxService, never())
            .createGroupJoinSuccessfulEvent(any(GroupJoinRequestEvent.class), any(Member.class));
        verify(outboxService, never()).saveOutboxEvent(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Does not update a member if member is not active")
    void disallowMemberUpdateWhenMemberIsNonActive() {
        final var event = GroupTestUtility.generateGroupJoinRequestEvent(group.id());

        given(outboxService.errorIfEventPublished(event))
            .willReturn(Mono.just(event));

        given(groupService.findById(group.id())).willReturn(Mono.just(group));

        given(memberRepository.save(any(Member.class))).willThrow(
            new DataAccessResourceFailureException(
                "Cannot update member because member status is not ACTIVE")
        );

        given(exceptionMapper.getBusinessException(any(Throwable.class)))
            .willReturn(new MemberNotActiveException(CANNOT_JOIN_GROUP));

        StepVerifier.create(memberService.joinGroup(event))
            .expectErrorMatches(throwable -> throwable instanceof MemberNotActiveException)
            .verify(Duration.ofSeconds(1));

        verify(outboxService).errorIfEventPublished(event);
        verify(groupService).findById(group.id());
        verify(memberRepository).save(any(Member.class));
        verify(exceptionMapper).getBusinessException(any(Throwable.class));
        verify(outboxService, never())
            .createGroupJoinSuccessfulEvent(any(GroupJoinRequestEvent.class), any(Member.class));
        verify(outboxService, never()).saveOutboxEvent(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Return error when group with id not found for join request")
    void returnErrorWhenGroupWithIdNotFoundForJoinRequest() {
        final var event = GroupTestUtility.generateGroupJoinRequestEvent();

        given(outboxService.errorIfEventPublished(event))
            .willReturn(Mono.just(event));

        given(groupService.findById(event.getAggregateId()))
            .willReturn(Mono.empty());

        given(exceptionMapper.getBusinessException(any(Throwable.class)))
            .willReturn(new GroupDoesNotExistException("Cannot add member to group"));

        StepVerifier.create(memberService.joinGroup(event))
            .expectErrorMatches(throwable -> throwable instanceof GroupDoesNotExistException)
            .verify(Duration.ofSeconds(1));

        verify(outboxService).errorIfEventPublished(event);
        verify(groupService).findById(event.getAggregateId());
        verify(memberRepository, never()).save(any(Member.class));
        verify(outboxService, never()).createGroupJoinSuccessfulEvent(any(), any());
        verify(outboxService, never()).saveOutboxEvent(any());
        verify(exceptionMapper).getBusinessException(any(Throwable.class));
    }

    @Test
    @DisplayName("Process group leave request successfully (removes a member from a group)")
    void processGroupLeave() throws JsonProcessingException {
        final var event = GroupTestUtility.generateGroupLeaveRequestEvent();
        final Long memberId = event.getMemberId();

        final OutboxEvent outboxEvent =  OutboxEvent.of(
            event.getEventId(), event.getAggregateId(),
            AggregateType.GROUP, EventType.MEMBER_LEFT,
            objectMapper.writeValueAsString(Collections.singletonMap("memberId", memberId)),
            EventStatus.SUCCESSFUL, event.getWebsocketId());

        final UUID socketId = UUID.fromString(event.getWebsocketId());

        given(outboxService.errorIfEventPublished(event))
            .willReturn(Mono.just(event));

        given(groupService.findById(event.getAggregateId()))
            .willReturn(Mono.just(group));

        given(memberRepository.findMemberByIdAndWebsocketId(memberId, socketId))
            .willReturn(Mono.just(Member.of(socketId.toString(), "User", group.id())));

        given(outboxService.createGroupLeaveSuccessfulEvent(event))
            .willReturn(Mono.just(outboxEvent));

        given(outboxService.saveOutboxEvent(any(OutboxEvent.class)))
            .willReturn(Mono.empty());

        given(memberRepository.removeMemberFromGroup(memberId, socketId))
            .willReturn(Mono.empty());

        StepVerifier.create(memberService.removeMember(event))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(outboxService).errorIfEventPublished(event);
        verify(groupService).findById(event.getAggregateId());
        verify(memberRepository).findMemberByIdAndWebsocketId(memberId, socketId);
        verify(outboxService).createGroupLeaveSuccessfulEvent(event);
        verify(outboxService).saveOutboxEvent(outboxEvent);
        verify(memberRepository).removeMemberFromGroup(memberId, socketId);
    }

    @Test
    @DisplayName("Return error when group with id not found for leave request")
    void returnErrorWhenGroupWithIdNotFoundForLeaveRequest() {
        final var event = GroupTestUtility.generateGroupLeaveRequestEvent();

        given(outboxService.errorIfEventPublished(event))
            .willReturn(Mono.just(event));

        given(groupService.findById(event.getAggregateId()))
            .willReturn(Mono.empty());

        given(exceptionMapper.getBusinessException(any(Throwable.class)))
            .willReturn(new GroupDoesNotExistException("Cannot remove member from group"));

        StepVerifier.create(memberService.removeMember(event))
            .expectErrorMatches(throwable -> throwable instanceof GroupDoesNotExistException)
            .verify(Duration.ofSeconds(1));

        verify(outboxService).errorIfEventPublished(event);
        verify(groupService).findById(event.getAggregateId());
        verify(memberRepository, never()).findMemberByIdAndWebsocketId(any(), any());
        verify(outboxService, never()).createGroupLeaveSuccessfulEvent(any());
        verify(outboxService, never()).saveOutboxEvent(any());
        verify(memberRepository, never()).removeMemberFromGroup(any(), any());
        verify(exceptionMapper).getBusinessException(any(Throwable.class));
    }

    @Test
    @DisplayName("Return error when member with socket id not found")
    void returnErrorWhenMemberWithSocketIdNotFound() {
        final var event = GroupTestUtility.generateGroupLeaveRequestEvent();
        final Long memberId = event.getMemberId();

        given(outboxService.errorIfEventPublished(event))
            .willReturn(Mono.just(event));

        given(groupService.findById(event.getAggregateId()))
            .willReturn(Mono.just(group));

        final UUID socketId = UUID.fromString(event.getWebsocketId());

        given(memberRepository.findMemberByIdAndWebsocketId(memberId, socketId))
            .willReturn(Mono.empty());

        given(exceptionMapper.getBusinessException(any(Throwable.class)))
            .willReturn(new MemberNotFoundException("Cannot remove member from group"));

        StepVerifier.create(memberService.removeMember(event))
            .expectErrorMatches(throwable -> throwable instanceof MemberNotFoundException)
            .verify(Duration.ofSeconds(1));

        verify(outboxService).errorIfEventPublished(event);
        verify(groupService).findById(event.getAggregateId());
        verify(memberRepository).findMemberByIdAndWebsocketId(memberId, socketId);
        verify(outboxService, never()).createGroupLeaveSuccessfulEvent(any());
        verify(outboxService, never()).saveOutboxEvent(any());
        verify(memberRepository, never()).removeMemberFromGroup(any(), any());
        verify(exceptionMapper).getBusinessException(any(Throwable.class));
    }

    @Test
    @DisplayName("Process group leave request failure successfully")
    void processGroupLeaveFailed() throws JsonProcessingException {
        final GroupLeaveRequestEvent event =
            GroupTestUtility.generateGroupLeaveRequestEvent();
        final Throwable failure = new RuntimeException();
        final OutboxEvent outboxEvent =  OutboxEvent.of(
            event.getEventId(), event.getAggregateId(),
            AggregateType.GROUP, EventType.MEMBER_LEFT,
            objectMapper.writeValueAsString(new ErrorData(failure.getMessage())),
            EventStatus.FAILED, event.getWebsocketId());

        given(outboxService.errorIfEventPublished(event))
            .willReturn(Mono.just(event));

        given(outboxService.createGroupLeaveFailedEvent(event, failure))
            .willReturn(Mono.just(outboxEvent));

        given(outboxService.saveOutboxEvent(any(OutboxEvent.class)))
            .willReturn(Mono.empty());

        given(exceptionMapper.getBusinessException(any(Throwable.class)))
            .willReturn(failure);

        StepVerifier.create(memberService.removeMemberFailed(event, failure))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(outboxService).errorIfEventPublished(event);
        verify(outboxService)
            .createGroupLeaveFailedEvent(event, failure);
        verify(outboxService).saveOutboxEvent(outboxEvent);
        verify(exceptionMapper).getBusinessException(any(Throwable.class));
        verify(memberRepository, never()).removeMemberFromGroup(any(), any());
    }

    @Test
    @DisplayName("Join request processor processes events only once")
    void doesNotProcessDuplicateJoinEvents() {
        final GroupJoinRequestEvent event = GroupTestUtility.generateGroupJoinRequestEvent();

        duplicateEventExists(() -> memberService.joinGroup(event), event);
    }

    @Test
    @DisplayName("Join request failure processor does not process the same event twice")
    void doesNotProcessDuplicateJoinFailedEvents() {
        final GroupJoinRequestEvent event = GroupTestUtility.generateGroupJoinRequestEvent();

        duplicateEventExists(
            () -> memberService.joinGroupFailed(event, new Throwable()), event);
    }

    @Test
    @DisplayName("Leave request processor processes events only once")
    void doesNotProcessDuplicateLeaveEvents() {
        final GroupLeaveRequestEvent event = GroupTestUtility.generateGroupLeaveRequestEvent();

        duplicateEventExists(() -> memberService.removeMember(event), event);
    }

    @Test
    @DisplayName("Leave request processor does not process the same event twice")
    void doesNotProcessDuplicateLeaveFailedEvents() {
        final GroupLeaveRequestEvent event = GroupTestUtility.generateGroupLeaveRequestEvent();

        duplicateEventExists(
            () -> memberService.removeMemberFailed(event, new Throwable()), event);
    }

    private void duplicateEventExists(Supplier<Mono<Void>> actionUnderTest, RequestEvent event) {
        given(outboxService.errorIfEventPublished(event))
            .willReturn(Mono.error(new EventAlreadyPublishedException("Cannot process event")));

        StepVerifier.create(actionUnderTest.get())
            .expectErrorMatches(Objects::nonNull)
            .verify(Duration.ofSeconds(1));

        verify(outboxService).errorIfEventPublished(event);
    }
}
