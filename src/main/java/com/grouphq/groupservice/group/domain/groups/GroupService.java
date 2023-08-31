package com.grouphq.groupservice.group.domain.groups;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * A service performing the main business logic for the Group Service application.
 */
@Service
public class GroupService {

    private final GroupRepository groupRepository;

    public GroupService(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    public Flux<Group> getGroups() {
        return groupRepository.findGroupsByStatus(GroupStatus.ACTIVE);
    }
}
