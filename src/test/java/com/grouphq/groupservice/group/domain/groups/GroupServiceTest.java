package com.grouphq.groupservice.group.domain.groups;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.grouphq.groupservice.group.domain.exceptions.EventAlreadyPublishedException;
import com.grouphq.groupservice.group.domain.exceptions.ExceptionMapper;
import com.grouphq.groupservice.group.domain.exceptions.GroupDoesNotExistException;
import com.grouphq.groupservice.group.domain.exceptions.GroupNotActiveException;
import com.grouphq.groupservice.group.domain.outbox.OutboxEvent;
import com.grouphq.groupservice.group.domain.outbox.OutboxService;
import com.grouphq.groupservice.group.domain.outbox.enums.AggregateType;
import com.grouphq.groupservice.group.domain.outbox.enums.EventStatus;
import com.grouphq.groupservice.group.domain.outbox.enums.EventType;
import com.grouphq.groupservice.group.event.daos.GroupCreateRequestEvent;
import com.grouphq.groupservice.group.event.daos.GroupStatusRequestEvent;
import com.grouphq.groupservice.group.event.daos.RequestEvent;
import com.grouphq.groupservice.group.testutility.GroupTestUtility;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Tag("UnitTest")
@ExtendWith(MockitoExtension.class)
class GroupServiceTest {
    @Mock
    private OutboxService outboxService;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private ExceptionMapper exceptionMapper;

    private ObjectMapper objectMapper;

    @InjectMocks
    private GroupService groupService;

    @BeforeEach
    public void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Gets (active) groups")
    void retrievesOnlyActiveGroups() {
        final Group[] testGroups = {
            GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE),
            GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE)
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
    @DisplayName("Processes group create request successfully")
    void processGroupCreateRequest() throws JsonProcessingException {
        final GroupCreateRequestEvent event = GroupTestUtility.generateGroupCreateRequestEvent();
        final Group group = GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE);
        final OutboxEvent outboxEvent = OutboxEvent.of(
            event.getEventId(), group.id(), AggregateType.GROUP,
            EventType.GROUP_CREATED, objectMapper.writeValueAsString(group),
            EventStatus.SUCCESSFUL, event.getWebsocketId()
        );

        given(outboxService.errorIfEventPublished(event))
            .willReturn(Mono.just(event));

        given(groupRepository.save(any(Group.class)))
            .willReturn(Mono.just(group));

        given(outboxService.createGroupCreateSuccessfulEvent(event, group))
            .willReturn(Mono.just(outboxEvent));

        given(outboxService.saveOutboxEvent(outboxEvent))
            .willReturn(Mono.empty());

        StepVerifier.create(groupService.createGroup(event))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(outboxService).errorIfEventPublished(event);
        verify(groupRepository).save(any(Group.class));
        verify(outboxService).createGroupCreateSuccessfulEvent(event, group);
        verify(exceptionMapper, never()).getBusinessException(any(Exception.class));
        verify(outboxService).saveOutboxEvent(outboxEvent);
    }

    @Test
    @DisplayName("Processes group create failure request successfully")
    void processGroupCreateFailureRequest() throws JsonProcessingException {
        final GroupCreateRequestEvent event = GroupTestUtility.generateGroupCreateRequestEvent();
        final Group group = GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE);
        final Throwable failure = new RuntimeException("Error");
        final OutboxEvent outboxEvent = OutboxEvent.of(
            event.getEventId(), group.id(), AggregateType.GROUP, EventType.GROUP_CREATED,
            objectMapper.writeValueAsString(failure.getMessage()),
            EventStatus.FAILED, event.getWebsocketId()
        );

        given(outboxService.errorIfEventPublished(event))
            .willReturn(Mono.just(event));

        given(outboxService.createGroupCreateFailedEvent(event, failure))
            .willReturn(Mono.just(outboxEvent));

        given(outboxService.saveOutboxEvent(outboxEvent))
            .willReturn(Mono.empty());

        StepVerifier.create(groupService.createGroupFailed(event, failure))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(outboxService).errorIfEventPublished(event);
        verify(groupRepository, never()).save(any(Group.class));
        verify(outboxService).createGroupCreateFailedEvent(event, failure);
        verify(exceptionMapper, never()).getBusinessException(any(Exception.class));
        verify(outboxService).saveOutboxEvent(outboxEvent);
    }

    @Test
    @DisplayName("Process group update status request successfully")
    void processGroupUpdateStatusRequest() throws JsonProcessingException {
        final Group group = GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE);
        final GroupStatusRequestEvent event = GroupTestUtility
            .generateGroupStatusRequestEvent(group.id(), GroupStatus.AUTO_DISBANDED);
        final OutboxEvent outboxEvent = OutboxEvent.of(
            event.getEventId(), group.id(), AggregateType.GROUP, EventType.GROUP_STATUS_UPDATED,
            objectMapper.writeValueAsString(
                Collections.singletonMap("status", event.getNewStatus())),
            EventStatus.SUCCESSFUL, event.getWebsocketId());

        given(outboxService.errorIfEventPublished(event))
            .willReturn(Mono.just(event));

        given(groupRepository.findById(event.getAggregateId()))
            .willReturn(Mono.just(group));

        given(groupRepository.updateStatus(event.getAggregateId(), event.getNewStatus()))
            .willReturn(Mono.empty());

        given(outboxService.createGroupStatusSuccessfulEvent(event))
            .willReturn(Mono.just(outboxEvent));

        given(outboxService.saveOutboxEvent(outboxEvent))
            .willReturn(Mono.empty());

        StepVerifier.create(groupService.updateGroupStatus(event))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(outboxService).errorIfEventPublished(event);
        verify(groupRepository).findById(event.getAggregateId());
        verify(groupRepository).updateStatus(event.getAggregateId(), event.getNewStatus());
        verify(outboxService).createGroupStatusSuccessfulEvent(event);
        verify(exceptionMapper, never()).getBusinessException(any(Exception.class));
        verify(outboxService).saveOutboxEvent(outboxEvent);
    }

    @Test
    @DisplayName("Throw error if group cannot be found")
    void processNonexistentGroupUpdateStatusRequest() {
        final GroupStatusRequestEvent event = GroupTestUtility
            .generateGroupStatusRequestEvent(10_000L, GroupStatus.ACTIVE);

        given(outboxService.errorIfEventPublished(event))
            .willReturn(Mono.just(event));

        given(groupRepository.findById(event.getAggregateId()))
            .willReturn(Mono.empty());

        given(exceptionMapper.getBusinessException(any(Exception.class)))
            .willReturn(new GroupDoesNotExistException("Cannot update group status"));

        StepVerifier.create(groupService.updateGroupStatus(event))
            .expectErrorMatches(throwable -> throwable instanceof GroupDoesNotExistException
                && "Cannot update group status because this group does not exist."
                                                 .equals(throwable.getMessage()))
            .verify(Duration.ofSeconds(1));

        verify(outboxService).errorIfEventPublished(event);
        verify(groupRepository).findById(event.getAggregateId());
        verify(groupRepository, never()).updateStatus(event.getAggregateId(), event.getNewStatus());
        verify(outboxService, never()).createGroupStatusSuccessfulEvent(event);
        verify(exceptionMapper).getBusinessException(any(Exception.class));
        verify(outboxService, never()).saveOutboxEvent(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Throw error if group status update from an in-active state")
    void processInvalidGroupUpdateStatusRequest() {
        final Group group = GroupTestUtility.generateFullGroupDetails(GroupStatus.AUTO_DISBANDED);
        final GroupStatusRequestEvent event = GroupTestUtility
            .generateGroupStatusRequestEvent(group.id(), GroupStatus.ACTIVE);

        given(outboxService.errorIfEventPublished(event))
            .willReturn(Mono.just(event));

        given(groupRepository.findById(event.getAggregateId()))
            .willReturn(Mono.just(group));

        given(groupRepository.updateStatus(group.id(), event.getNewStatus()))
            .willReturn(Mono.error(new RuntimeException()));

        given(exceptionMapper.getBusinessException(any(Exception.class)))
            .willReturn(new GroupNotActiveException("Cannot update group status"));

        StepVerifier.create(groupService.updateGroupStatus(event))
            .expectErrorMatches(throwable -> throwable instanceof GroupNotActiveException
                && "Cannot update group status because this group is not active."
                                                 .equals(throwable.getMessage()))
            .verify(Duration.ofSeconds(1));

        verify(outboxService).errorIfEventPublished(event);
        verify(groupRepository).findById(event.getAggregateId());
        verify(groupRepository).updateStatus(event.getAggregateId(), event.getNewStatus());
        verify(outboxService, never()).createGroupStatusSuccessfulEvent(event);
        verify(exceptionMapper).getBusinessException(any(Exception.class));
        verify(outboxService, never()).saveOutboxEvent(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Process group update status failure request successfully")
    void processGroupUpdateStatusRequestFailure() throws JsonProcessingException {
        final Group group = GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE);
        final GroupStatusRequestEvent event = GroupTestUtility
            .generateGroupStatusRequestEvent(group.id(), GroupStatus.AUTO_DISBANDED);
        final OutboxEvent outboxEvent = OutboxEvent.of(
            event.getEventId(), group.id(), AggregateType.GROUP, EventType.GROUP_STATUS_UPDATED,
            objectMapper.writeValueAsString(
                Collections.singletonMap("status", event.getNewStatus())),
            EventStatus.SUCCESSFUL, event.getWebsocketId());
        final Throwable failure = new RuntimeException("Error");

        given(outboxService.errorIfEventPublished(event))
            .willReturn(Mono.just(event));

        given(outboxService.createGroupStatusFailedEvent(event, failure))
            .willReturn(Mono.just(outboxEvent));

        given(outboxService.saveOutboxEvent(outboxEvent))
            .willReturn(Mono.empty());

        StepVerifier.create(groupService.updateGroupStatusFailed(event, failure))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(outboxService).errorIfEventPublished(event);
        verify(outboxService).createGroupStatusFailedEvent(event, failure);
        verify(outboxService).saveOutboxEvent(outboxEvent);
        verifyNoInteractions(groupRepository, exceptionMapper);
    }

    @Test
    @DisplayName("Process group cutoff request")
    void expireGroups() {
        final Instant now = Instant.now();
        given(groupRepository.getActiveGroupsPastCutoffDate(now, GroupStatus.ACTIVE))
            .willReturn(Flux.empty());

        StepVerifier.create(groupService.getActiveGroupsPastCutoffDate(now))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(groupRepository)
            .getActiveGroupsPastCutoffDate(now, GroupStatus.ACTIVE);
    }

    @Test
    @DisplayName("Group create processor does not process the same event twice")
    void doesNotProcessDuplicateCreateEvents() {
        final GroupCreateRequestEvent event = GroupTestUtility.generateGroupCreateRequestEvent();

        duplicateEventExists(() -> groupService.createGroup(event), event);
    }

    @Test
    @DisplayName("Group create failure processor does not process the same event twice")
    void doesNotProcessDuplicateCreateEventsFailure() {
        final GroupCreateRequestEvent event = GroupTestUtility.generateGroupCreateRequestEvent();

        duplicateEventExists(
            () -> groupService.createGroupFailed(event, new Throwable()), event);
    }

    @Test
    @DisplayName("Group status update processor does not process the same event twice")
    void doesNotProcessDuplicateStatusUpdateEvents() {
        final Group group = GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE);
        final GroupStatusRequestEvent event = GroupTestUtility
            .generateGroupStatusRequestEvent(group.id(), GroupStatus.AUTO_DISBANDED);

        duplicateEventExists(() -> groupService.updateGroupStatus(event), event);
    }

    @Test
    @DisplayName("Group status update failure processor does not process the same event twice")
    void doesNotProcessDuplicateStatusUpdateEventsFailure() {
        final Group group = GroupTestUtility.generateFullGroupDetails(GroupStatus.ACTIVE);
        final GroupStatusRequestEvent event = GroupTestUtility
            .generateGroupStatusRequestEvent(group.id(), GroupStatus.AUTO_DISBANDED);

        duplicateEventExists(
            () -> groupService.updateGroupStatusFailed(event, new Throwable()), event);
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

    private void duplicateEventExists(Supplier<Mono<Void>> actionUnderTest, RequestEvent event) {
        given(outboxService.errorIfEventPublished(event))
            .willReturn(Mono.error(new EventAlreadyPublishedException("Cannot process event")));

        StepVerifier.create(actionUnderTest.get())
            .expectErrorMatches(Objects::nonNull)
            .verify(Duration.ofSeconds(1));

        verify(outboxService).errorIfEventPublished(event);
    }
}
