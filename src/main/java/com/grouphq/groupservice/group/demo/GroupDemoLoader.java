package com.grouphq.groupservice.group.demo;

import com.grouphq.groupservice.group.domain.groups.Group;
import com.grouphq.groupservice.group.domain.groups.GroupRepository;
import com.grouphq.groupservice.group.domain.groups.GroupService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A Spring Job scheduler for periodically adding active groups
 * and auto-disbanding expired groups.
 */
@Component
public class GroupDemoLoader {

    private boolean initialStateLoaded;

    private final int initialGroupSize;

    private final int periodicGroupAdditionCount;

    private final GroupRepository groupRepository;

    private final GroupService groupService;

    /**
     * Gathers dependencies and values needed for demo loader.
     */
    public GroupDemoLoader(GroupService groupService,

                           GroupRepository groupRepository,

                           @Value("${group.loader.initial-group-size}")
                           int initialGroupSize,

                           @Value("${group.loader.periodic-group-addition-count}")
                           int periodicGroupAdditionCount
    ) {
        this.groupService = groupService;
        this.groupRepository = groupRepository;

        this.initialGroupSize = initialGroupSize;
        this.periodicGroupAdditionCount = periodicGroupAdditionCount;
    }

    @Scheduled(initialDelayString = "${group.loader.initial-group-delay}",
        fixedDelayString = "${group.loader.periodic-group-addition-interval}",
        timeUnit = TimeUnit.SECONDS)
    void loadGroups() {
        final int groupsToAdd = initialStateLoaded
            ? periodicGroupAdditionCount : initialGroupSize;

        for (int i = 0; i < groupsToAdd; i++) {
            final Group group = groupService.generateGroup();
            assert groupRepository != null;
            groupRepository.save(group).subscribe();
        }
        initialStateLoaded = true;
    }

    @Scheduled(initialDelayString = "${group.expiry-checker.initial-check-delay}",
        fixedDelayString = "${group.expiry-checker.check-interval}",
        timeUnit = TimeUnit.SECONDS)
    void expireGroups() {
        groupService.expireGroups().subscribe();
    }
}
