package org.grouphq.groupservice.group.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.grouphq.groupservice.group.domain.exceptions.EventAlreadyPublishedException;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.domain.members.Member;
import org.grouphq.groupservice.group.domain.outbox.enums.AggregateType;
import org.grouphq.groupservice.group.domain.outbox.enums.EventStatus;
import org.grouphq.groupservice.group.domain.outbox.enums.EventType;
import org.grouphq.groupservice.group.event.daos.GroupCreateRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupJoinRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupLeaveRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupStatusRequestEvent;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
import org.junit.jupiter.api.BeforeEach;
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

@ExtendWith(MockitoExtension.class)
@Tag("UnitTest")
class OutboxServiceTest {
    @Mock
    private OutboxRepository outboxRepository;

    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxService outboxService;

    private static GroupCreateRequestEvent requestEvent;

    @BeforeEach
    void setUp() {
        objectMapper.registerModule(new JavaTimeModule());

        requestEvent = new GroupCreateRequestEvent(UUID.randomUUID(), "Title", "Description",
            10, 1, "system", "websocketId", Instant.now());
    }

    @Test
    @DisplayName("Saves an event to the database")
    void saveOutboxEvent() throws JsonProcessingException {
        final Member member = GroupTestUtility.generateFullMemberDetails("User", 1L);

        final OutboxEvent outboxEvent = OutboxEvent.of(
            UUID.randomUUID(), 1L, AggregateType.GROUP,
            EventType.MEMBER_JOINED, objectMapper.writeValueAsString(member),
            EventStatus.SUCCESSFUL, "websocketId");

        given(outboxRepository.save(
            outboxEvent.getEventId(), outboxEvent.getAggregateId(), outboxEvent.getAggregateType(),
            outboxEvent.getEventType(), outboxEvent.getEventData(), outboxEvent.getEventStatus(),
            outboxEvent.getWebsocketId(), outboxEvent.getCreatedDate()
        )).willReturn(Mono.empty());

        StepVerifier.create(outboxService.saveOutboxEvent(outboxEvent))
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(outboxRepository)
            .save(
                outboxEvent.getEventId(), outboxEvent.getAggregateId(),
                outboxEvent.getAggregateType(), outboxEvent.getEventType(),
                outboxEvent.getEventData(), outboxEvent.getEventStatus(),
                outboxEvent.getWebsocketId(), outboxEvent.getCreatedDate()
            );
    }

    @Test
    @DisplayName("Gets outbox events from the database sorted by created date ascending")
    void retrieveOutboxEvents() {
        final OutboxEvent[] outboxEvents = {
            GroupTestUtility.generateOutboxEvent(),
            GroupTestUtility.generateOutboxEvent(),
            GroupTestUtility.generateOutboxEvent()
        };

        given(outboxRepository.findAllOrderByCreatedDateAsc())
            .willReturn(Flux.just(outboxEvents));

        StepVerifier.create(outboxService.getOutboxEvents())
            .expectNext(outboxEvents[0])
            .expectNext(outboxEvents[1])
            .expectNext(outboxEvents[2])
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(outboxRepository).findAllOrderByCreatedDateAsc();
    }

    @Test
    @DisplayName("Verify that an event has been posted")
    void checkIfEventPosted() {
        given(outboxRepository.existsById(requestEvent.getEventId()))
            .willReturn(Mono.just(true));

        StepVerifier.create(outboxService.errorIfEventPublished(requestEvent))
            .expectError(EventAlreadyPublishedException.class)
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Verify that an event has not been posted")
    void checkIfEventNotPosted() {
        given(outboxRepository.existsById(requestEvent.getEventId()))
            .willReturn(Mono.just(false));

        StepVerifier.create(outboxService.errorIfEventPublished(requestEvent))
            .expectNext(requestEvent)
            .expectComplete()
            .verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Deletes event from the database, then return that same event")
    void deleteEvent() {
        final OutboxEvent outboxEvent = OutboxEvent.of(
            UUID.randomUUID(), 1L, AggregateType.GROUP,
            EventType.MEMBER_JOINED, "eventData",
            EventStatus.SUCCESSFUL, "websocketId");
        given(outboxRepository.deleteById(outboxEvent.getEventId()))
            .willReturn(Mono.empty());

        StepVerifier.create(outboxService.deleteEvent(outboxEvent))
            .expectNext(outboxEvent)
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(outboxRepository).deleteById(outboxEvent.getEventId());
    }

    @Test
    @DisplayName("Returns an outbox event with correct group join successful data")
    void verifyGroupJoinSuccessfulDataReturned() {
        final GroupJoinRequestEvent requestEvent = GroupTestUtility.generateGroupJoinRequestEvent();
        final Member member = GroupTestUtility
            .generateFullMemberDetails(requestEvent.getUsername(), requestEvent.getAggregateId());

        StepVerifier.create(outboxService.createGroupJoinSuccessfulEvent(requestEvent, member))
            .consumeNextWith(event -> assertThat(event).satisfies(outboxEvent -> {
                assertThat(outboxEvent.getEventId())
                    .isEqualTo(requestEvent.getEventId());
                assertThat(outboxEvent.getAggregateId())
                    .isEqualTo(requestEvent.getAggregateId());
                assertThat(outboxEvent.getAggregateType())
                    .isEqualTo(AggregateType.GROUP);
                assertThat(outboxEvent.getEventType())
                    .isEqualTo(EventType.MEMBER_JOINED);
                assertThat(objectMapper.readValue(event.getEventData(), Member.class))
                    .isEqualTo(member);
                assertThat(outboxEvent.getEventStatus())
                    .isEqualTo(EventStatus.SUCCESSFUL);
                assertThat(outboxEvent.getWebsocketId())
                    .isEqualTo(requestEvent.getWebsocketId());
            }))
            .expectComplete().verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Returns an outbox event with correct group join failed data")
    void verifyGroupJoinFailedDataReturned() {
        final GroupJoinRequestEvent requestEvent = GroupTestUtility.generateGroupJoinRequestEvent();
        final Throwable failure = new Throwable("Failed to join group");

        StepVerifier.create(outboxService.createGroupJoinFailedEvent(requestEvent, failure))
            .consumeNextWith(event -> assertThat(event).satisfies(outboxEvent -> {
                assertThat(outboxEvent.getEventId())
                    .isEqualTo(requestEvent.getEventId());
                assertThat(outboxEvent.getAggregateId())
                    .isEqualTo(requestEvent.getAggregateId());
                assertThat(outboxEvent.getAggregateType())
                    .isEqualTo(AggregateType.GROUP);
                assertThat(outboxEvent.getEventType())
                    .isEqualTo(EventType.MEMBER_JOINED);
                assertThat(objectMapper.readValue(event.getEventData(), ErrorData.class))
                    .isEqualTo(new ErrorData(failure.getMessage()));
                assertThat(outboxEvent.getEventStatus())
                    .isEqualTo(EventStatus.FAILED);
                assertThat(outboxEvent.getWebsocketId())
                    .isEqualTo(requestEvent.getWebsocketId());
            }))
            .expectComplete().verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Returns an outbox event with correct group leave successful data")
    void verifyGroupLeaveSuccessfulDataReturned() {
        final GroupLeaveRequestEvent requestEvent =
            GroupTestUtility.generateGroupLeaveRequestEvent();

        StepVerifier.create(outboxService.createGroupLeaveSuccessfulEvent(requestEvent))
            .consumeNextWith(event -> assertThat(event).satisfies(outboxEvent -> {
                assertThat(outboxEvent.getEventId())
                    .isEqualTo(requestEvent.getEventId());
                assertThat(outboxEvent.getAggregateId())
                    .isEqualTo(requestEvent.getAggregateId());
                assertThat(outboxEvent.getAggregateType())
                    .isEqualTo(AggregateType.GROUP);
                assertThat(outboxEvent.getEventType())
                    .isEqualTo(EventType.MEMBER_LEFT);
                assertThat(objectMapper.readValue(outboxEvent.getEventData(),
                    new TypeReference<Map<String, Object>>() {})).isNotNull()
                    .isEqualTo(
                        Collections.singletonMap("memberId", requestEvent.getMemberId()));
                assertThat(outboxEvent.getEventStatus())
                    .isEqualTo(EventStatus.SUCCESSFUL);
                assertThat(outboxEvent.getWebsocketId())
                    .isEqualTo(requestEvent.getWebsocketId());
            }))
            .expectComplete().verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Returns an outbox event with correct group leave failed data")
    void verifyGroupLeaveFailedDataReturned() {
        final GroupLeaveRequestEvent requestEvent =
            GroupTestUtility.generateGroupLeaveRequestEvent();
        final Throwable failure = new Throwable("Failed to leave group");

        StepVerifier.create(outboxService.createGroupLeaveFailedEvent(requestEvent, failure))
            .consumeNextWith(event -> assertThat(event).satisfies(outboxEvent -> {
                assertThat(outboxEvent.getEventId())
                    .isEqualTo(requestEvent.getEventId());
                assertThat(outboxEvent.getAggregateId())
                    .isEqualTo(requestEvent.getAggregateId());
                assertThat(outboxEvent.getAggregateType())
                    .isEqualTo(AggregateType.GROUP);
                assertThat(outboxEvent.getEventType())
                    .isEqualTo(EventType.MEMBER_LEFT);
                assertThat(objectMapper.readValue(event.getEventData(), ErrorData.class))
                    .isEqualTo(new ErrorData(failure.getMessage()));
                assertThat(outboxEvent.getEventStatus())
                    .isEqualTo(EventStatus.FAILED);
                assertThat(outboxEvent.getWebsocketId())
                    .isEqualTo(requestEvent.getWebsocketId());
            }))
            .expectComplete().verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Returns an outbox event with correct group created successful data")
    void verifyGroupCreatedDataReturned() {
        final Group group = GroupTestUtility.generateFullGroupDetails(1L, GroupStatus.ACTIVE);
        final GroupCreateRequestEvent requestEvent =
            GroupTestUtility.generateGroupCreateRequestEvent();

        StepVerifier.create(outboxService.createGroupCreateSuccessfulEvent(requestEvent, group))
            .consumeNextWith(event -> assertThat(event).satisfies(outboxEvent -> {
                assertThat(outboxEvent.getEventId())
                    .isEqualTo(requestEvent.getEventId());
                assertThat(outboxEvent.getAggregateId())
                    .isEqualTo(group.id());
                assertThat(outboxEvent.getAggregateType())
                    .isEqualTo(AggregateType.GROUP);
                assertThat(outboxEvent.getEventType())
                    .isEqualTo(EventType.GROUP_CREATED);
                assertThat(objectMapper.readValue(outboxEvent.getEventData(), Group.class))
                    .isEqualTo(group);
                assertThat(outboxEvent.getEventStatus())
                    .isEqualTo(EventStatus.SUCCESSFUL);
                assertThat(outboxEvent.getWebsocketId())
                    .isEqualTo(requestEvent.getWebsocketId());
            }))
            .expectComplete().verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Returns an outbox event with correct group created failed data")
    void verifyGroupCreatedFailedDataReturned() {
        final GroupCreateRequestEvent requestEvent =
            GroupTestUtility.generateGroupCreateRequestEvent();
        final Throwable failure = new Throwable("Failed to create group");

        StepVerifier.create(outboxService.createGroupCreateFailedEvent(requestEvent, failure))
            .consumeNextWith(event -> assertThat(event).satisfies(outboxEvent -> {
                assertThat(outboxEvent.getEventId())
                    .isEqualTo(requestEvent.getEventId());
                assertThat(outboxEvent.getAggregateId()).isNull();
                assertThat(outboxEvent.getAggregateType())
                    .isEqualTo(AggregateType.GROUP);
                assertThat(outboxEvent.getEventType())
                    .isEqualTo(EventType.GROUP_CREATED);
                assertThat(objectMapper.readValue(event.getEventData(), ErrorData.class))
                    .isEqualTo(new ErrorData(failure.getMessage()));
                assertThat(outboxEvent.getEventStatus())
                    .isEqualTo(EventStatus.FAILED);
                assertThat(outboxEvent.getWebsocketId())
                    .isEqualTo(requestEvent.getWebsocketId());
            }))
            .expectComplete().verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Returns an outbox event with correct group status updated successful data")
    void verifyGroupStatusSuccessfulDataReturned() {
        final GroupStatusRequestEvent requestEvent =
            GroupTestUtility.generateGroupStatusRequestEvent(1L, GroupStatus.ACTIVE);

        StepVerifier.create(outboxService.createGroupStatusSuccessfulEvent(requestEvent))
            .consumeNextWith(event -> assertThat(event).satisfies(outboxEvent -> {
                assertThat(outboxEvent.getEventId())
                    .isEqualTo(requestEvent.getEventId());
                assertThat(outboxEvent.getAggregateId())
                    .isEqualTo(requestEvent.getAggregateId());
                assertThat(outboxEvent.getAggregateType())
                    .isEqualTo(AggregateType.GROUP);
                assertThat(outboxEvent.getEventType())
                    .isEqualTo(EventType.GROUP_STATUS_UPDATED);
                assertThat(objectMapper.readValue(outboxEvent.getEventData(),
                    new TypeReference<Map<String, Object>>() {})).isNotNull()
                    .isEqualTo(Collections.singletonMap("status",
                        requestEvent.getNewStatus().toString()));
                assertThat(outboxEvent.getEventStatus())
                    .isEqualTo(EventStatus.SUCCESSFUL);
                assertThat(outboxEvent.getWebsocketId())
                    .isEqualTo(requestEvent.getWebsocketId());
            }))
            .expectComplete().verify(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Returns an outbox event with correct group status updated failed data")
    void verifyGroupStatusFailedDataReturned() {
        final GroupStatusRequestEvent requestEvent =
            GroupTestUtility.generateGroupStatusRequestEvent(1L, GroupStatus.ACTIVE);
        final Throwable failure = new Throwable("Failed to update group status");

        StepVerifier.create(outboxService.createGroupStatusFailedEvent(requestEvent, failure))
            .consumeNextWith(event -> assertThat(event).satisfies(outboxEvent -> {
                assertThat(outboxEvent.getEventId())
                    .isEqualTo(requestEvent.getEventId());
                assertThat(outboxEvent.getAggregateId())
                    .isEqualTo(requestEvent.getAggregateId());
                assertThat(outboxEvent.getAggregateType())
                    .isEqualTo(AggregateType.GROUP);
                assertThat(outboxEvent.getEventType())
                    .isEqualTo(EventType.GROUP_STATUS_UPDATED);
                assertThat(objectMapper.readValue(event.getEventData(), ErrorData.class))
                    .isEqualTo(new ErrorData(failure.getMessage()));
                assertThat(outboxEvent.getEventStatus())
                    .isEqualTo(EventStatus.FAILED);
                assertThat(outboxEvent.getWebsocketId())
                    .isEqualTo(requestEvent.getWebsocketId());
            }))
            .expectComplete().verify(Duration.ofSeconds(1));
    }
}
