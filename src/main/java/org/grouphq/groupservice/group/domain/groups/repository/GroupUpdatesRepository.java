package org.grouphq.groupservice.group.domain.groups.repository;

import java.time.Instant;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import reactor.core.publisher.Mono;

/**
 * Interface to perform Reactive update operations against the repository's "groups" table.
 */
public interface GroupUpdatesRepository {

    Mono<Group> updateStatusByGroupId(Long groupId, GroupStatus status);

    Mono<Group> updatedLastMemberActivityByGroupId(Long groupId, Instant lastMemberActivity);
}
