package org.grouphq.groupservice.group.domain.members;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grouphq.groupservice.group.domain.exceptions.ExceptionMapper;
import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.grouphq.groupservice.group.domain.outbox.OutboxService;
import org.grouphq.groupservice.group.event.daos.GroupUpdatedEvent;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupJoinRequestEvent;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupLeaveRequestEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * A service for handling events related to group members.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MemberEventService {

    private final OutboxService outboxService;

    private final GroupService groupService;

    private final ExceptionMapper exceptionMapper;

    @Transactional
    public Mono<Void> joinGroup(GroupJoinRequestEvent event) {

        log.info("Received join request: {}", event);
        return outboxService.errorIfEventPublished(event)
            .flatMap(requestEvent ->
                groupService.addMember(
                    event.getAggregateId(), event.getUsername(), event.getWebsocketId()))
            .flatMap(member -> outboxService.createGroupJoinSuccessfulEvent(event, member))
            .flatMap(outboxService::saveOutboxEvent)
            .then(sendGroupUpdateEvent(event.getAggregateId()))
            .doOnSuccess(emptySave ->
                log.info("Fulfilled join request. Saved new member and outbox event: {}", event))
            .log()
            .onErrorMap(exceptionMapper::getBusinessException);
    }

    @Transactional
    public Mono<Void> joinGroupFailed(GroupJoinRequestEvent event,
                                      Throwable throwable) {

        log.info("Received join request: {}", event);
        return outboxService.errorIfEventPublished(event)
            .flatMap(requestEvent -> outboxService.createGroupJoinFailedEvent(event, throwable))
            .flatMap(outboxService::saveOutboxEvent)
            .doOnSuccess(emptySave ->
                log.info("Fulfilled join request. Saved outbox event: {}", event))
            .log()
            .onErrorMap(exceptionMapper::getBusinessException);
    }


    @Transactional
    public Mono<Void> removeMember(GroupLeaveRequestEvent event) {

        log.info("Received remove member request: {}", event);
        return outboxService.errorIfEventPublished(event)
            .flatMap(requestEvent ->
                groupService.removeMember(
                    event.getAggregateId(), event.getMemberId(), event.getWebsocketId()))
            .then(outboxService.createGroupLeaveSuccessfulEvent(event))
            .flatMap(outboxService::saveOutboxEvent)
            .then(sendGroupUpdateEvent(event.getAggregateId()))
            .doOnSuccess(emptySave ->
                log.info("Fulfilled remove request. "
                    + "Removed member from group and saved outbox event: {}", event))
            .log()
            .onErrorMap(exceptionMapper::getBusinessException);
    }

    @Transactional
    public Mono<Void> removeMemberFailed(GroupLeaveRequestEvent event,
                                         Throwable throwable) {

        log.info("Received remove member request: {}", event);
        return outboxService.errorIfEventPublished(event)
            .flatMap(requestEvent -> outboxService.createGroupLeaveFailedEvent(
                event, exceptionMapper.getBusinessException(throwable)))
            .flatMap(outboxService::saveOutboxEvent)
            .doOnSuccess(emptySave ->
                log.info("Fulfilled remove request. Saved outbox event: {}", event))
            .log()
            .onErrorMap(exceptionMapper::getBusinessException);
    }

    private Mono<Void> sendGroupUpdateEvent(Long groupId) {
        return groupService.findGroupById(groupId)
            .flatMap(group -> outboxService.createGroupUpdateSuccessfulEvent(
                new GroupUpdatedEvent(groupId), group, null)
            )
            .flatMap(outboxService::saveOutboxEvent);
    }
}
