package org.grouphq.groupservice.group.domain.groups;

import com.github.javafaker.Faker;
import java.time.Instant;
import org.grouphq.groupservice.group.domain.exceptions.ExceptionMapper;
import org.grouphq.groupservice.group.domain.exceptions.GroupDoesNotExistException;
import org.grouphq.groupservice.group.domain.outbox.OutboxEvent;
import org.grouphq.groupservice.group.domain.outbox.OutboxService;
import org.grouphq.groupservice.group.event.daos.GroupCreateRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupStatusRequestEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;

/**
 * A service for performing business logic related to groups.
 */
@Service
public class GroupService {
    private static final Logger LOG = Loggers.getLogger(GroupService.class);

    private final OutboxService outboxService;

    private final GroupRepository groupRepository;

    private final ExceptionMapper exceptionMapper;


    public GroupService(OutboxService outboxService,
                        GroupRepository groupRepository,
                        ExceptionMapper exceptionMapper) {
        this.outboxService = outboxService;
        this.groupRepository = groupRepository;
        this.exceptionMapper = exceptionMapper;
    }

    public Mono<Group> findById(Long id) {
        return groupRepository.findById(id);
    }

    public Flux<Group> getGroups() {
        return groupRepository.findGroupsByStatus(GroupStatus.ACTIVE);
    }

    @Transactional
    public Mono<Void> createGroup(GroupCreateRequestEvent event) {

        LOG.debug("Received create group request: {}", event);
        return outboxService.errorIfEventPublished(event)
            .flatMap(this::createGroupCreateEvent)
            .flatMap(outboxService::saveOutboxEvent)
            .doOnSuccess(emptySave -> LOG.info("Fulfilled create request: {}", event))
            .log(LOG)
            .onErrorMap(exceptionMapper::getBusinessException);
    }

    private Mono<OutboxEvent> createGroupCreateEvent(GroupCreateRequestEvent createRequestEvent) {
        final Group group = Group.of(
            createRequestEvent.getTitle(), createRequestEvent.getDescription(),
            createRequestEvent.getMaxGroupSize(), createRequestEvent.getCurrentGroupSize(),
            GroupStatus.ACTIVE);

        return groupRepository.save(group)
            .flatMap(savedGroup -> outboxService
                .createGroupCreateSuccessfulEvent(createRequestEvent, savedGroup));
    }

    @Transactional
    public Mono<Void> createGroupFailed(GroupCreateRequestEvent event,
                                        Throwable throwable) {

        LOG.debug("Received create group failed request: {}", event);
        return outboxService.errorIfEventPublished(event)
            .flatMap(requestEvent -> outboxService.createGroupCreateFailedEvent(event, throwable))
            .flatMap(outboxService::saveOutboxEvent)
            .doOnSuccess(emptySave -> LOG.info("Fulfilled create request: {}", event))
            .log(LOG)
            .onErrorMap(exceptionMapper::getBusinessException);
    }

    @Transactional
    public Mono<Void> updateGroupStatus(GroupStatusRequestEvent event) {

        LOG.debug("Received update status failed request: {}", event);
        return outboxService.errorIfEventPublished(event)
            .flatMap(requestEvent -> groupRepository.findById(event.getAggregateId()))
            .switchIfEmpty(Mono.error(new GroupDoesNotExistException("Cannot update group status")))
            .flatMap(group -> updateStatus(group, event.getNewStatus()))
            .flatMap(savedGroup -> outboxService.createGroupStatusSuccessfulEvent(event))
            .flatMap(outboxService::saveOutboxEvent)
            .doOnSuccess(emptySave -> LOG.info("Fulfilled update status request: {}", event))
            .log(LOG)
            .onErrorMap(exceptionMapper::getBusinessException);
    }

    private Mono<Group> updateStatus(Group group, GroupStatus newStatus) {
        return groupRepository.updateStatus(group.id(), newStatus)
            .thenReturn(group);
    }

    @Transactional
    public Mono<Void> updateGroupStatusFailed(GroupStatusRequestEvent event,
                                              Throwable throwable) {

        LOG.debug("Received update status failed request: {}", event);
        return outboxService.errorIfEventPublished(event)
            .flatMap(requestEvent -> outboxService.createGroupStatusFailedEvent(event, throwable))
            .flatMap(outboxService::saveOutboxEvent)
            .doOnSuccess(emptySave -> LOG.info("Fulfilled update status request: {}", event))
            .log(LOG)
            .onErrorMap(exceptionMapper::getBusinessException);
    }

    public Flux<Group> getActiveGroupsPastCutoffDate(Instant cutoffDate) {
        return groupRepository.getActiveGroupsPastCutoffDate(cutoffDate, GroupStatus.ACTIVE);
    }

    public Group generateGroup() {
        final Faker faker = new Faker();
        
        final int currentCapacity = 0;
        final int maxCapacity = faker.number().numberBetween(2, 64);

        return Group.of(faker.lorem().sentence(), faker.lorem().sentence(20),
            maxCapacity, currentCapacity, GroupStatus.ACTIVE);
    }
}
