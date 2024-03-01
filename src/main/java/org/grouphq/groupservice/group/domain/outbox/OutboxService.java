package org.grouphq.groupservice.group.domain.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grouphq.groupservice.group.domain.exceptions.EventAlreadyPublishedException;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.members.Member;
import org.grouphq.groupservice.group.domain.outbox.enums.AggregateType;
import org.grouphq.groupservice.group.domain.outbox.enums.EventStatus;
import org.grouphq.groupservice.group.domain.outbox.enums.EventType;
import org.grouphq.groupservice.group.event.daos.Event;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupCreateRequestEvent;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupJoinRequestEvent;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupLeaveRequestEvent;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupStatusRequestEvent;
import org.grouphq.groupservice.group.event.daos.requestevent.RequestEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A service for performing business logic related to the outbox.
 *
 * <p> Since Spring Data JDBC doesn't support JSON types or automatic casting
 * of String objects to JSON in a Postgres database, we need to define
 * custom queries. These queries don't allow navigating object graphs, so
 * we need to unpack the object. To avoid this cluttering the code, we abstract
 * that logic to this OutboxService class. We also abstract the Outbox instance creation
 * (from our GroupUpdateResult model) to this class.</p>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class OutboxService {

    private final OutboxRepository outboxRepository;

    public Flux<OutboxEvent> getOutboxEvents() {
        return outboxRepository.findAllOrderByCreatedDateAsc();
    }

    public Mono<Void> saveOutboxEvent(OutboxEvent outboxEvent) {
        return outboxRepository.save(
            outboxEvent.getEventId(),
            outboxEvent.getAggregateId(),
            outboxEvent.getAggregateType(),
            outboxEvent.getEventType(),
            outboxEvent.getEventData(),
            outboxEvent.getEventStatus(),
            outboxEvent.getWebsocketId(),
            outboxEvent.getCreatedDate()
        );
    }

    public Mono<OutboxEvent> createGroupJoinSuccessfulEvent(
        GroupJoinRequestEvent joinRequest,
        Member member) {

        return Mono.fromCallable(() -> {
            log.debug("Creating Group Join Update Successful Result...");
            return OutboxEvent.of(
                joinRequest.getEventId(),
                joinRequest.getAggregateId(),
                AggregateType.GROUP,
                EventType.MEMBER_JOINED,
                member,
                EventStatus.SUCCESSFUL,
                joinRequest.getWebsocketId()
            );
        })
        .doOnNext(result ->
            log.info("Created Group Join Update Successful Result: {}", result))
        .doOnError(error ->
            log.error("Error while creating Group Join Update Successful Result: ", error));
    }

    public Mono<OutboxEvent> createGroupJoinFailedEvent(
            GroupJoinRequestEvent joinRequest,
            Throwable failure) {

        return Mono.fromCallable(() -> {
            log.debug("Creating Group Join Update Failed Result...");
            return OutboxEvent.of(
                joinRequest.getEventId(),
                joinRequest.getAggregateId(),
                AggregateType.GROUP,
                EventType.MEMBER_JOINED,
                new ErrorData(failure.getMessage()),
                EventStatus.FAILED,
                joinRequest.getWebsocketId()
            );
        })
        .doOnNext(result ->
            log.info("Created Group Join Update Failed Result: {}", result))
        .doOnError(error ->
            log.error("Error while creating Group Join Update Failed Result: ", error));
    }

    public Mono<OutboxEvent> createGroupLeaveSuccessfulEvent(
        GroupLeaveRequestEvent leaveRequest,
        Member member) {

        return Mono.fromCallable(() -> {
            log.debug("Creating Group Leave Update Successful Result...");
            return OutboxEvent.of(
                leaveRequest.getEventId(),
                leaveRequest.getAggregateId(),
                AggregateType.GROUP,
                EventType.MEMBER_LEFT,
                member,
                EventStatus.SUCCESSFUL,
                leaveRequest.getWebsocketId()
            );
        })
            .doOnNext(result ->
                log.info("Created Group Leave Update Successful Result: {}", result))
            .doOnError(error ->
                log.error("Error while creating Group Leave Update Successful Result: ", error));
    }

    public Mono<OutboxEvent> createGroupLeaveFailedEvent(
        GroupLeaveRequestEvent leaveRequest,
        Throwable failure) {

        return Mono.fromCallable(() -> {
            log.debug("Creating Group Leave Update Failed Result...");
            return OutboxEvent.of(
                leaveRequest.getEventId(),
                leaveRequest.getAggregateId(),
                AggregateType.GROUP,
                EventType.MEMBER_LEFT,
                new ErrorData(failure.getMessage()),
                EventStatus.FAILED,
                leaveRequest.getWebsocketId()
            );
        })
            .doOnNext(result ->
                log.info("Created Group Leave Update Failed Result: {}", result))
            .doOnError(error ->
                log.error("Error while creating Group Leave Update Failed Result: ", error));
    }

    public Mono<OutboxEvent> createGroupCreateSuccessfulEvent(
        GroupCreateRequestEvent createRequest,
        Group group) {

        return Mono.fromCallable(() -> {
            log.debug("Creating Group Create Update Successful Result...");
            return OutboxEvent.of(
                createRequest.getEventId(),
                group.id(),
                AggregateType.GROUP,
                EventType.GROUP_CREATED,
                group,
                EventStatus.SUCCESSFUL,
                createRequest.getWebsocketId()
            );
        })
            .doOnNext(result ->
                log.info("Created Group Create Update Successful Result: {}", result))
            .doOnError(error ->
                log.error("Error while creating Group Create Update Successful Result: ", error));
    }
    
    public Mono<OutboxEvent> createGroupCreateFailedEvent(
        GroupCreateRequestEvent createRequest,
        Throwable failure) {

        return Mono.fromCallable(() -> {
            log.debug("Creating Group Create Update Failed Result...");
            return OutboxEvent.of(
                createRequest.getEventId(),
                null,
                AggregateType.GROUP,
                EventType.GROUP_CREATED,
                new ErrorData(failure.getMessage()),
                EventStatus.FAILED,
                createRequest.getWebsocketId()
            );
        })
            .doOnNext(result ->
                log.info("Created Group Create Update Failed Result: {}", result))
            .doOnError(error ->
                log.error("Error while creating Group Create Update Failed Result: ", error));
    }

    public Mono<OutboxEvent> createGroupUpdateSuccessfulEvent(
        Event event, Group group, String websocketId) {

        return Mono.fromCallable(() -> {
            log.debug("Creating Group Status Update Successful Result...");
            return OutboxEvent.of(
                event.getEventId(),
                event.getAggregateId(),
                AggregateType.GROUP,
                EventType.GROUP_UPDATED,
                group,
                EventStatus.SUCCESSFUL,
                websocketId
            );
        })
            .doOnNext(result ->
                log.info("Created Group Status Update Successful Result: {}", result))
            .doOnError(error ->
                log.error("Error while creating Group Status Update Successful Result: ", error));
    }

    public Mono<OutboxEvent> createGroupUpdateFailedEvent(
        GroupStatusRequestEvent statusRequest,
        Throwable failure) {

        return Mono.fromCallable(() -> {
            log.debug("Creating Group Status Update Failed Result...");
            return OutboxEvent.of(
                statusRequest.getEventId(),
                statusRequest.getAggregateId(),
                AggregateType.GROUP,
                EventType.GROUP_UPDATED,
                new ErrorData(failure.getMessage()),
                EventStatus.FAILED,
                statusRequest.getWebsocketId()
            );
        })
            .doOnNext(result ->
                log.info("Created Group Status Update Failed Result: {}", result))
            .doOnError(error ->
                log.error("Error while creating Group Status Update Failed Result: ", error));
    }

    public <T extends RequestEvent> Mono<T> errorIfEventPublished(T event) {
        return outboxRepository.existsById(event.getEventId())
            .flatMap(exists -> {
                if (exists) {
                    log.debug("Event already posted: {}", event);
                    return Mono.error(new EventAlreadyPublishedException(
                        "Event already published"));
                } else {
                    return Mono.just(event);
                }
            });
    }

    @Transactional
    public Mono<OutboxEvent> deleteEvent(OutboxEvent outboxEvent) {
        return outboxRepository.deleteById(outboxEvent.getEventId())
            .thenReturn(outboxEvent);
    }

}
