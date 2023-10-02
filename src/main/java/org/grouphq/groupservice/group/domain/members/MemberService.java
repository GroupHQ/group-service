package org.grouphq.groupservice.group.domain.members;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grouphq.groupservice.group.domain.exceptions.ExceptionMapper;
import org.grouphq.groupservice.group.domain.exceptions.GroupDoesNotExistException;
import org.grouphq.groupservice.group.domain.exceptions.MemberNotFoundException;
import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.grouphq.groupservice.group.domain.outbox.OutboxService;
import org.grouphq.groupservice.group.event.daos.GroupJoinRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupLeaveRequestEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A service for performing business logic related to members.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MemberService {
    private final MemberRepository memberRepository;
    private final GroupService groupService;
    private final OutboxService outboxService;
    private final ExceptionMapper exceptionMapper;

    public Flux<Member> getActiveMembers(Long groupId) {
        log.info("Getting all active members for group with id: {}", groupId);
        return memberRepository.getActiveMembersByGroup(groupId);
    }

    @Transactional
    public Mono<Void> joinGroup(GroupJoinRequestEvent event) {

        log.info("Received join request: {}", event);
        return outboxService.errorIfEventPublished(event)
            .flatMap(requestEvent -> groupService.findById(event.getAggregateId()))
            .switchIfEmpty(Mono.error(new GroupDoesNotExistException("Cannot save member")))
            .flatMap(group -> memberRepository.save(
                Member.of(event.getWebsocketId(), event.getUsername(), group.id())))
            .flatMap(member -> outboxService.createGroupJoinSuccessfulEvent(event, member))
            .flatMap(outboxService::saveOutboxEvent)
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
            .flatMap(requestEvent -> groupService.findById(event.getAggregateId()))
            .switchIfEmpty(Mono.error(new GroupDoesNotExistException("Cannot remove member")))
            .then(Mono.defer(() -> memberRepository.findMemberByIdAndWebsocketId(
                event.getMemberId(), UUID.fromString(event.getWebsocketId()))))
            .switchIfEmpty(Mono.error(new MemberNotFoundException("Cannot remove member")))
            .then(Mono.defer(() -> memberRepository.removeMemberFromGroup(
                event.getMemberId(), UUID.fromString(event.getWebsocketId()))))
            .then(Mono.defer(() -> outboxService.createGroupLeaveSuccessfulEvent(event)))
            .flatMap(outboxService::saveOutboxEvent)
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
}
