package org.grouphq.groupservice.group.demo;

import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupRepository;
import org.grouphq.groupservice.group.domain.groups.GroupService;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.event.daos.GroupStatusRequestEvent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A Spring Job scheduler for periodically adding active groups
 * and auto-disbanding expired groups.
 */
public class GroupDemoLoader {

    private static final Logger LOG = LoggerFactory.getLogger(GroupDemoLoader.class);

    private boolean initialStateLoaded;

    @Value("${group.loader.initial-group-size}")
    private int initialGroupSize;

    @Value("${group.loader.periodic-group-addition-count}")
    private int periodicGroupAdditionCount;

    @Value("${group.cutoff-checker.time}")
    private int groupLifetime;

    private final GroupRepository groupRepository;

    private final GroupService groupService;

    /**
     * Gathers dependencies and values needed for demo loader.
     */
    public GroupDemoLoader(GroupService groupService, GroupRepository groupRepository) {
        this.groupService = groupService;
        this.groupRepository = groupRepository;
    }

    @Scheduled(initialDelayString = "${group.loader.initial-group-delay}",
        fixedDelayString = "${group.loader.periodic-group-addition-interval}",
        timeUnit = TimeUnit.SECONDS)
    private void loadGroupsJob() {
        loadGroups(initialGroupSize, periodicGroupAdditionCount).subscribe();
    }

    public Flux<Group> loadGroups(int initialGroupSize,
                                  int periodicGroupAdditionCount) {
        final int groupsToAdd = initialStateLoaded
            ? periodicGroupAdditionCount : initialGroupSize;

        initialStateLoaded = true;
        Group[] groups = new Group[groupsToAdd];

        for (int i = 0; i < groupsToAdd; i++) {
            groups[i] = groupService.generateGroup();
        }

        return groupRepository.saveAll(Flux.just(groups));
    }

    @Scheduled(initialDelayString = "${group.cutoff-checker.initial-check-delay}",
        fixedDelayString = "${group.cutoff-checker.check-interval}",
        timeUnit = TimeUnit.SECONDS)
    private void expireGroupsJob() {
        expireGroups(Instant.now().minus(groupLifetime, ChronoUnit.SECONDS)).subscribe();
    }

    public Flux<Void> expireGroups(Instant cutoffDate) {
        return groupService.getActiveGroupsPastCutoffDate(cutoffDate)
            .flatMap(group -> {
                final GroupStatusRequestEvent statusRequestEvent = new GroupStatusRequestEvent(
                    UUID.randomUUID(), group.id(), GroupStatus.AUTO_DISBANDED,
                    null, Instant.now());

                return groupService.updateGroupStatus(statusRequestEvent)
                    .onErrorResume(throwable ->
                        groupService.updateGroupStatusFailed(statusRequestEvent, throwable))
                    .onErrorResume(throwable -> {
                        LOG.error("Error updating group status", throwable);
                        // log to sentry
                        return Mono.empty();
                    });
            });
    }
}
