package org.grouphq.groupservice.group.demo;

import com.github.javafaker.Faker;
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
import org.grouphq.groupservice.group.event.daos.GroupCreateRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupJoinRequestEvent;
import org.grouphq.groupservice.group.event.daos.GroupStatusRequestEvent;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A Spring Job scheduler for periodically adding active groups
 * and auto-disbanding expired groups.
 */
@Slf4j
@RequiredArgsConstructor
public class GroupDemoLoader {

    private final GroupProperties groupProperties;

    private boolean initialStateLoaded;

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

        return Flux.just(generateCreateGroupEvents(groupsToAdd))
            .delayElements(Duration.ofSeconds(1))
            .flatMap(groupEventService::createGroup)
            .onErrorResume(throwable -> {
                log.error("Error creating group", throwable);
                // log to sentry
                return Mono.empty();
            });
    }

    private GroupCreateRequestEvent[] generateCreateGroupEvents(int groupsToAdd) {
        GroupCreateRequestEvent[] createRequestEvents = new GroupCreateRequestEvent[groupsToAdd];

        for (int i = 0; i < groupsToAdd; i++) {
            final Group group = groupService.generateGroup();
            createRequestEvents[i] = new GroupCreateRequestEvent(
                UUID.randomUUID(), group.title(), group.description(),
                group.maxGroupSize(), "system", null,
                Instant.now());
        }

        return createRequestEvents;
    }

    @Scheduled(initialDelayString = "${group.loader.group-service-jobs.expire-groups.initial-delay}",
        fixedDelayString = "${group.loader.group-service-jobs.expire-groups.fixed-delay}",
        timeUnit = TimeUnit.SECONDS)
    private void expireGroupJob() {
        final var expireGroupJobProperties = groupProperties.getLoader().getGroupServiceJobs().getExpireGroups();
        final Instant cutoffDate = Instant.now().minus(expireGroupJobProperties.getGroupLifetime(), ChronoUnit.SECONDS);

        expireGroups(cutoffDate).subscribe();
    }

    public Flux<Void> expireGroups(Instant cutoffDate) {
        return groupService.findActiveGroupsPastCutoffDate(cutoffDate)
            .flatMap(group -> {
                final GroupStatusRequestEvent statusRequestEvent = new GroupStatusRequestEvent(
                    UUID.randomUUID(), group.id(), GroupStatus.AUTO_DISBANDED,
                    null, Instant.now());

                return groupEventService.updateGroupStatus(statusRequestEvent)
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
                                .then(memberEventService.joinGroup(groupJoinRequestEvent))
                        );
                } else {
                    return Mono.empty();
                }
            });
    }

    private Mono<GroupJoinRequestEvent> createGroupJoinEvent(Long groupId) {
        final Faker faker = new Faker();

        return Mono.just(
            new GroupJoinRequestEvent(
                UUID.randomUUID(), groupId, faker.name().firstName(),
                UUID.randomUUID().toString(), Instant.now())
        );
    }

    private Mono<Long> randomDelay(int maxTimeSeconds) {
        return Mono.delay(
            Duration.ofSeconds((long) (Math.random() * maxTimeSeconds) + 1)
        );
    }
}
