package org.grouphq.groupservice.group.domain.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.grouphq.groupservice.group.domain.exceptions.EventAlreadyPublishedException;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.members.Member;
import org.grouphq.groupservice.group.domain.outbox.enums.AggregateType;
import org.grouphq.groupservice.group.domain.outbox.enums.EventStatus;
import org.grouphq.groupservice.group.domain.outbox.enums.EventType;
import org.grouphq.groupservice.group.event.daos.GroupCreateRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupJoinRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupLeaveRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupStatusRequestEvent;
import org.grouphq.groupservice.group.event.daos.RequestEvent;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
@Service
public class OutboxService {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxRepository outboxRepository;

    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
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
            LOG.debug("Creating Group Join Update Successful Result...");
            return OutboxEvent.of(
                joinRequest.getEventId(),
                joinRequest.getAggregateId(),
                AggregateType.GROUP,
                EventType.MEMBER_JOINED,
                objectMapper.writeValueAsString(member),
                EventStatus.SUCCESSFUL,
                joinRequest.getWebsocketId()
            );
        })
        .doOnNext(result ->
            LOG.info("Created Group Join Update Successful Result: {}", result))
        .doOnError(error ->
            LOG.error("Error while creating Group Join Update Successful Result: ", error));
    }

    public Mono<OutboxEvent> createGroupJoinFailedEvent(
            GroupJoinRequestEvent joinRequest,
            Throwable failure) {

        return Mono.fromCallable(() -> {
            LOG.debug("Creating Group Join Update Failed Result...");
            return OutboxEvent.of(
                joinRequest.getEventId(),
                joinRequest.getAggregateId(),
                AggregateType.GROUP,
                EventType.MEMBER_JOINED,
                objectMapper.writeValueAsString(new ErrorData(failure.getMessage())),
                EventStatus.FAILED,
                joinRequest.getWebsocketId()
            );
        })
        .doOnNext(result ->
            LOG.info("Created Group Join Update Failed Result: {}", result))
        .doOnError(error ->
            LOG.error("Error while creating Group Join Update Failed Result: ", error));
    }

    public Mono<OutboxEvent> createGroupLeaveSuccessfulEvent(GroupLeaveRequestEvent leaveRequest) {

        return Mono.fromCallable(() -> {
            LOG.debug("Creating Group Leave Update Successful Result...");
            return OutboxEvent.of(
                leaveRequest.getEventId(),
                leaveRequest.getAggregateId(),
                AggregateType.GROUP,
                EventType.MEMBER_LEFT,
                objectMapper.writeValueAsString(
                    Collections.singletonMap("memberId", leaveRequest.getMemberId())),
                EventStatus.SUCCESSFUL,
                leaveRequest.getWebsocketId()
            );
        })
            .doOnNext(result ->
                LOG.info("Created Group Leave Update Successful Result: {}", result))
            .doOnError(error ->
                LOG.error("Error while creating Group Leave Update Successful Result: ", error));
    }

    public Mono<OutboxEvent> createGroupLeaveFailedEvent(
        GroupLeaveRequestEvent leaveRequest,
        Throwable failure) {

        return Mono.fromCallable(() -> {
            LOG.debug("Creating Group Leave Update Successful Result...");
            return OutboxEvent.of(
                leaveRequest.getEventId(),
                leaveRequest.getAggregateId(),
                AggregateType.GROUP,
                EventType.MEMBER_LEFT,
                objectMapper.writeValueAsString(new ErrorData(failure.getMessage())),
                EventStatus.FAILED,
                leaveRequest.getWebsocketId()
            );
        })
            .doOnNext(result ->
                LOG.info("Created Group Leave Update Failed Result: {}", result))
            .doOnError(error ->
                LOG.error("Error while creating Group Leave Update Failed Result: ", error));
    }

    public Mono<OutboxEvent> createGroupCreateSuccessfulEvent(
        GroupCreateRequestEvent createRequest,
        Group group) {

        return Mono.fromCallable(() -> {
            LOG.debug("Creating Group Create Update Successful Result...");
            return OutboxEvent.of(
                createRequest.getEventId(),
                group.id(),
                AggregateType.GROUP,
                EventType.GROUP_CREATED,
                objectMapper.writeValueAsString(group),
                EventStatus.SUCCESSFUL,
                createRequest.getWebsocketId()
            );
        })
            .doOnNext(result ->
                LOG.info("Created Group Create Update Successful Result: {}", result))
            .doOnError(error ->
                LOG.error("Error while creating Group Create Update Successful Result: ", error));
    }
    
    public Mono<OutboxEvent> createGroupCreateFailedEvent(
        GroupCreateRequestEvent createRequest,
        Throwable failure) {

        return Mono.fromCallable(() -> {
            LOG.debug("Creating Group Create Update Failed Result...");
            return OutboxEvent.of(
                createRequest.getEventId(),
                null,
                AggregateType.GROUP,
                EventType.GROUP_CREATED,
                objectMapper.writeValueAsString(new ErrorData(failure.getMessage())),
                EventStatus.FAILED,
                createRequest.getWebsocketId()
            );
        })
            .doOnNext(result ->
                LOG.info("Created Group Create Update Failed Result: {}", result))
            .doOnError(error ->
                LOG.error("Error while creating Group Create Update Failed Result: ", error));
    }

    public Mono<OutboxEvent> createGroupStatusSuccessfulEvent(
        GroupStatusRequestEvent statusRequest) {

        return Mono.fromCallable(() -> {
            LOG.debug("Creating Group Status Update Successful Result...");
            return OutboxEvent.of(
                statusRequest.getEventId(),
                statusRequest.getAggregateId(),
                AggregateType.GROUP,
                EventType.GROUP_STATUS_UPDATED,
                objectMapper.writeValueAsString(
                    Collections.singletonMap("status", statusRequest.getNewStatus())),
                EventStatus.SUCCESSFUL,
                statusRequest.getWebsocketId()
            );
        })
            .doOnNext(result ->
                LOG.info("Created Group Status Update Successful Result: {}", result))
            .doOnError(error ->
                LOG.error("Error while creating Group Status Update Successful Result: ", error));
    }

    public Mono<OutboxEvent> createGroupStatusFailedEvent(
        GroupStatusRequestEvent statusRequest,
        Throwable failure) {

        return Mono.fromCallable(() -> {
            LOG.debug("Creating Group Status Update Failed Result...");
            return OutboxEvent.of(
                statusRequest.getEventId(),
                statusRequest.getAggregateId(),
                AggregateType.GROUP,
                EventType.GROUP_STATUS_UPDATED,
                objectMapper.writeValueAsString(new ErrorData(failure.getMessage())),
                EventStatus.FAILED,
                statusRequest.getWebsocketId()
            );
        })
            .doOnNext(result ->
                LOG.info("Created Group Status Update Failed Result: {}", result))
            .doOnError(error ->
                LOG.error("Error while creating Group Status Update Failed Result: ", error));
    }

    public <T extends RequestEvent> Mono<T> errorIfEventPublished(T event) {
        return outboxRepository.existsById(event.getEventId())
            .flatMap(exists -> {
                if (exists) {
                    LOG.debug("Event already posted: {}", event);
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
