package org.grouphq.groupservice.group.domain.members;

import org.grouphq.groupservice.group.domain.exceptions.ExceptionMapper;
import org.grouphq.groupservice.group.domain.exceptions.GroupDoesNotExistException;
import org.grouphq.groupservice.group.domain.groups.GroupRepository;
import org.grouphq.groupservice.group.domain.outbox.OutboxService;
import org.grouphq.groupservice.group.event.daos.GroupJoinRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupLeaveRequestEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;

/**
 * A service for performing business logic related to members.
 */
@Service
public class MemberService {
    private static final Logger LOG = Loggers.getLogger(MemberService.class);

    private final MemberRepository memberRepository;

    private final GroupRepository groupRepository;

    private final OutboxService outboxService;

    private final ExceptionMapper exceptionMapper;

    public MemberService(MemberRepository memberRepository,
                         GroupRepository groupRepository,
                         OutboxService outboxService,
                         ExceptionMapper exceptionMapper) {
        this.memberRepository = memberRepository;
        this.groupRepository = groupRepository;
        this.outboxService = outboxService;
        this.exceptionMapper = exceptionMapper;
    }

    public Flux<Member> getActiveMembers(Long groupId) {
        return memberRepository.getActiveMembersByGroup(groupId);
    }


    @Transactional
    public Mono<Void> joinGroup(GroupJoinRequestEvent event) {

        LOG.debug("Handling join request: {}", event);
        return outboxService.errorIfEventPublished(event)
            .flatMap(requestEvent -> groupRepository.findById(event.getAggregateId()))
            .switchIfEmpty(Mono.error(new GroupDoesNotExistException("Cannot save member")))
            .flatMap(group -> memberRepository.save(Member.of(event.getUsername(), group.id())))
            .flatMap(member -> outboxService.createGroupJoinSuccessfulEvent(event, member))
            .flatMap(outboxService::saveOutboxEvent)
            .doOnSuccess(emptySave -> LOG.info("Fulfilled join request: {}", event))
            .log(LOG)
            .onErrorMap(exceptionMapper::getBusinessException);
    }

    @Transactional
    public Mono<Void> joinGroupFailed(GroupJoinRequestEvent event,
                                      Throwable throwable) {

        LOG.debug("Handling join request failure: {}", event);
        return outboxService.errorIfEventPublished(event)
            .flatMap(requestEvent -> outboxService.createGroupJoinFailedEvent(event, throwable))
            .flatMap(outboxService::saveOutboxEvent)
            .doOnSuccess(emptySave -> LOG.info("Fulfilled join request: {}", event))
            .log(LOG)
            .onErrorMap(exceptionMapper::getBusinessException);
    }


    @Transactional
    public Mono<Void> removeMember(GroupLeaveRequestEvent event) {

        LOG.debug("Handling remove member request: {}", event);
        return outboxService.errorIfEventPublished(event)
            .flatMap(requestEvent -> groupRepository.findById(event.getAggregateId()))
            .switchIfEmpty(Mono.error(new GroupDoesNotExistException("Cannot remove member")))
            .flatMap(unused -> memberRepository.removeMemberFromGroup(event.getMemberId()))
            .then(Mono.defer(() -> outboxService.createGroupLeaveSuccessfulEvent(event)))
            .flatMap(outboxService::saveOutboxEvent)
            .doOnSuccess(emptySave -> LOG.info("Fulfilled remove request: {}", event))
            .log(LOG)
            .onErrorMap(exceptionMapper::getBusinessException);
    }

    @Transactional
    public Mono<Void> removeMemberFailed(GroupLeaveRequestEvent event,
                                         Throwable throwable) {

        LOG.debug("Handling remove member request failure : {}", event);
        return outboxService.errorIfEventPublished(event)
            .flatMap(requestEvent -> outboxService.createGroupLeaveFailedEvent(
                event, exceptionMapper.getBusinessException(throwable)))
            .flatMap(outboxService::saveOutboxEvent)
            .doOnSuccess(emptySave -> LOG.info("Fulfilled remove request failure: {}", event))
            .log(LOG)
            .onErrorMap(exceptionMapper::getBusinessException);
    }
}
