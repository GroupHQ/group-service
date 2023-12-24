package org.grouphq.groupservice.group.domain.groups.repository;

import java.time.Instant;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

/**
 * Interface to perform Reactive operations against the repository's "groups" table.
 * Spring dependency injects a bean implementing this interface at runtime.
 */
public interface GroupRepository
    extends ReactiveCrudRepository<Group, Long>, GroupUpdatesRepository {

    @Query("SELECT * FROM groups WHERE status = :status ORDER BY created_date DESC")
    Flux<Group> findGroupsByStatus(GroupStatus status);

    @Query("SELECT * FROM groups")
    Flux<Group> getAllGroups();

    @Query("SELECT * FROM groups WHERE status = :status AND groups.created_date < :cutoffDate")
    Flux<Group> getActiveGroupsPastCutoffDate(Instant cutoffDate, GroupStatus status);
}
