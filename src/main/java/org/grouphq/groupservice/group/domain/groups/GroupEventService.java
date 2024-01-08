package org.grouphq.groupservice.group.domain.groups;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grouphq.groupservice.group.domain.exceptions.ExceptionMapper;
import org.grouphq.groupservice.group.domain.outbox.OutboxEvent;
import org.grouphq.groupservice.group.domain.outbox.OutboxService;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupCreateRequestEvent;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupStatusRequestEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * A service for performing business logic related to groups.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class GroupEventService {

    private final OutboxService outboxService;

    private final GroupService groupService;

    private final ExceptionMapper exceptionMapper;


    @Transactional
    public Mono<Group> createGroup(GroupCreateRequestEvent event) {

        log.info("Received create group request: {}", event);
        return outboxService.errorIfEventPublished(event)
            .flatMap(this::createGroupCreateEvent)
            .flatMap(outboxEventAndGroup -> {
                final OutboxEvent outboxEvent = outboxEventAndGroup.getT1();
                final Group group = outboxEventAndGroup.getT2();

                return outboxService.saveOutboxEvent(outboxEvent)
                    .thenReturn(group);
            })
            .doOnSuccess(emptySave ->
                log.info("Fulfilled create request. Created group and outbox event: {}", event))
            .log()
            .onErrorMap(exceptionMapper::getBusinessException);
    }

    private Mono<Tuple2<OutboxEvent, Group>> createGroupCreateEvent(GroupCreateRequestEvent createRequestEvent) {
        return groupService
            .createGroup(
                createRequestEvent.getTitle(),
                createRequestEvent.getDescription(),
                createRequestEvent.getMaxGroupSize())
            .flatMap(savedGroup -> outboxService
                .createGroupCreateSuccessfulEvent(createRequestEvent, savedGroup)
                .map(outboxEvent -> Tuples.of(outboxEvent, savedGroup)));
    }

    @Transactional
    public Mono<Void> createGroupFailed(GroupCreateRequestEvent event,
                                        Throwable throwable) {

        log.info("Received create group request: {}", event);
        return outboxService.errorIfEventPublished(event)
            .flatMap(requestEvent -> outboxService.createGroupCreateFailedEvent(event, throwable))
            .flatMap(outboxService::saveOutboxEvent)
            .doOnSuccess(emptySave ->
                log.info("Fulfilled create request. Saved outbox event: {}", event))
            .log()
            .onErrorMap(exceptionMapper::getBusinessException);
    }

    @Transactional
    public Mono<Void> autoDisbandGroup(GroupStatusRequestEvent event) {
        if (event.getNewStatus() != GroupStatus.AUTO_DISBANDED) {
            throw new IllegalArgumentException("New status must be DISBANDED");
        }

        log.info("Received auto-disband group request: {}", event);
        return outboxService.errorIfEventPublished(event)
            .flatMap(requestEvent ->
                groupService.disbandGroup(event.getAggregateId(), GroupStatus.AUTO_DISBANDED))
            .flatMap(group -> outboxService.createGroupUpdateSuccessfulEvent(event, group, event.getWebsocketId()))
            .flatMap(outboxService::saveOutboxEvent)
            .doOnSuccess(emptySave ->
                log.info("Fulfilled auto-disband group request. "
                    + "Updated status and saved outbox event: {}", event))
            .log()
            .onErrorMap(exceptionMapper::getBusinessException);
    }

    /**
     * A method for updating a group's status.
     *
     * @param event The event containing the group ID and the new status
     * @return A Mono that completes when the status has been updated
     * @deprecated Recommended to use {@link #autoDisbandGroup(GroupStatusRequestEvent)} for disbanding groups
     */
    @Transactional
    @Deprecated
    public Mono<Void> updateGroupStatus(GroupStatusRequestEvent event) {
        log.info("Received update status request: {}", event);
        return outboxService.errorIfEventPublished(event)
            .flatMap(group ->
                groupService.updateStatus(event.getAggregateId(), event.getNewStatus()))
            .flatMap(group -> outboxService.createGroupUpdateSuccessfulEvent(event, group, event.getWebsocketId()))
            .flatMap(outboxService::saveOutboxEvent)
            .doOnSuccess(emptySave ->
                log.info("Fulfilled update status request. "
                    + "Updated status and saved outbox event: {}", event))
            .log()
            .onErrorMap(exceptionMapper::getBusinessException);
    }

    @Transactional
    public Mono<Void> updateGroupStatusFailed(GroupStatusRequestEvent event,
                                              Throwable throwable) {

        log.info("Received update status request: {}", event);
        return outboxService.errorIfEventPublished(event)
            .flatMap(requestEvent -> outboxService.createGroupUpdateFailedEvent(event, throwable))
            .flatMap(outboxService::saveOutboxEvent)
            .doOnSuccess(emptySave -> log.info("Fulfilled update status request: {}", event))
            .log()
            .onErrorMap(exceptionMapper::getBusinessException);
    }
}
