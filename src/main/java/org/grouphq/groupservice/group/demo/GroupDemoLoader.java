package org.grouphq.groupservice.group.demo;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grouphq.groupservice.config.GroupProperties;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupEventService;
import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.domain.members.MemberEventService;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupCreateRequestEvent;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupJoinRequestEvent;
import org.grouphq.groupservice.group.event.daos.requestevent.GroupStatusRequestEvent;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * A Spring Job scheduler for periodically adding active groups
 * and auto-disbanding expired groups.
 */
@Slf4j
@RequiredArgsConstructor
public class GroupDemoLoader {

    private final GroupProperties groupProperties;

    private boolean initialStateLoaded;

    private final GroupGeneratorService generateGroupPostingService;

    private final GroupService groupService;

    private final GroupEventService groupEventService;

    private final MemberEventService memberEventService;

    @Scheduled(initialDelayString = "${group.loader.group-service-jobs.load-groups.initial-delay}",
        fixedDelayString = "${group.loader.group-service-jobs.load-groups.fixed-delay}",
        timeUnit = TimeUnit.SECONDS)
    private void loadGroupsJob() {
        final var loadGroupsJobProperties = groupProperties.getLoader().getGroupServiceJobs().getLoadGroups();
        loadGroups(
            loadGroupsJobProperties.getInitialGroupCount(),
            loadGroupsJobProperties.getFixedGroupAdditionCount()
        ).subscribe();
    }

    public Flux<Void> loadGroups(int initialGroupCount, int fixedGroupAdditionCount) {
        final int groupsToAdd = initialStateLoaded ? fixedGroupAdditionCount : initialGroupCount;

        initialStateLoaded = true;

        return generateCreateGroupEvents(groupsToAdd)
            .delayElements(Duration.ofSeconds(1))
            .flatMap(groupRequestAndCharacterTuple -> {
                final GroupCreateRequestEvent groupCreateRequestEvent = groupRequestAndCharacterTuple.getT1();
                final CharacterEntity characterEntity = groupRequestAndCharacterTuple.getT2();

                return groupEventService.createGroup(groupCreateRequestEvent)
                    .flatMap(group -> {
                        final GroupJoinRequestEvent groupJoinRequestEvent = new GroupJoinRequestEvent(
                            UUID.randomUUID(), group.id(), characterEntity.getName(),
                            UUID.randomUUID().toString(), Instant.now());

                        return memberEventService.joinGroup(groupJoinRequestEvent)
                            .onErrorResume(throwable ->
                                groupEventService.createGroupFailed(groupCreateRequestEvent, throwable))
                            .onErrorResume(throwable -> {
                                log.error("Error creating group", throwable);
                                // log to sentry
                                return Mono.empty();
                            });
                    });
            })
            .onErrorResume(throwable -> {
                log.error("Error creating group", throwable);
                // log to sentry
                return Mono.empty();
            });
    }

    private Flux<Tuple2<GroupCreateRequestEvent, CharacterEntity>> generateCreateGroupEvents(int groupsToAdd) {
        return Flux.range(0, groupsToAdd)
            .flatMap(i -> generateGroupPostingService.generateGroup(CharacterEntity.createRandomCharacter()))
            .map(groupCharacterTuple2 -> {
                final Group group = groupCharacterTuple2.getT1();
                final CharacterEntity characterEntity = groupCharacterTuple2.getT2();

                final GroupCreateRequestEvent groupCreateRequestEvent = new GroupCreateRequestEvent(
                    UUID.randomUUID(), group.title(), group.description(),
                    group.maxGroupSize(), "system", null,
                    Instant.now());

                return Tuples.of(groupCreateRequestEvent, characterEntity);
            });
    }

    @Scheduled(initialDelayString = "${group.loader.group-service-jobs.expire-groups.initial-delay}",
        fixedDelayString = "${group.loader.group-service-jobs.expire-groups.fixed-delay}",
        timeUnit = TimeUnit.SECONDS)
    private void expireGroupJob() {
        final var expireGroupJobProperties = groupProperties.getLoader().getGroupServiceJobs().getExpireGroups();
        final Instant cutoffDate = Instant.now().minus(expireGroupJobProperties.getGroupLifetime(), ChronoUnit.SECONDS);

        expireGroupsCreatedBefore(cutoffDate).subscribe();
    }

    public Flux<Void> expireGroupsCreatedBefore(Instant cutoffDate) {
        return groupService.findActiveGroupsCreatedBefore(cutoffDate)
            .flatMap(group -> {
                final GroupStatusRequestEvent statusRequestEvent = new GroupStatusRequestEvent(
                    UUID.randomUUID(), group.id(), GroupStatus.AUTO_DISBANDED,
                    null, Instant.now());

                return groupEventService.autoDisbandGroup(statusRequestEvent)
                    .onErrorResume(throwable ->
                        groupEventService.updateGroupStatusFailed(statusRequestEvent, throwable))
                    .onErrorResume(throwable -> {
                        log.error("Error updating group status", throwable);
                        // log to sentry
                        return Mono.empty();
                    });
            });
    }

    @Scheduled(initialDelayString = "${group.loader.group-service-jobs.load-members.initial-delay}",
        fixedDelayString = "${group.loader.group-service-jobs.load-members.fixed-delay}",
        timeUnit = TimeUnit.SECONDS)
    private void loadMemberJob() {
        final var loadMembersJobProperties = groupProperties.getLoader().getGroupServiceJobs().getLoadMembers();

        loadMembers(loadMembersJobProperties.getMemberJoinMaxDelay()).subscribe();
    }

    public Flux<Void> loadMembers(int memberJoinMaxDelay) {
        return groupService.findActiveGroupsWithMembers()
            .flatMap(group -> {
                if (group.members().size() < group.maxGroupSize()) {
                    return createGroupJoinEvent(group.id())
                        .flatMap(groupJoinRequestEvent ->
                            randomDelay(memberJoinMaxDelay)
                                .then(joinGroup(group.id(), groupJoinRequestEvent))
                        );
                } else {
                    return Mono.empty();
                }
            });
    }

    private Mono<GroupJoinRequestEvent> createGroupJoinEvent(Long groupId) {
        final CharacterEntity characterEntity = CharacterEntity.createRandomCharacter();

        return Mono.just(
            new GroupJoinRequestEvent(
                UUID.randomUUID(), groupId, characterEntity.getName(),
                UUID.randomUUID().toString(), Instant.now())
        );
    }

    private Mono<Long> randomDelay(int maxTimeSeconds) {
        return Mono.delay(
            Duration.ofSeconds((long) (Math.random() * maxTimeSeconds) + 1)
        );
    }

    private Mono<Void> joinGroup(Long groupId, GroupJoinRequestEvent groupJoinRequestEvent) {
        return groupService.findGroupByIdWithActiveMembers(groupId)
            .flatMap(group -> {
                if (group.status() == GroupStatus.ACTIVE && group.members().size() < group.maxGroupSize()) {
                    return memberEventService.joinGroup(groupJoinRequestEvent);
                } else {
                    return Mono.empty();
                }
            });
    }
}
