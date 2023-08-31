package com.grouphq.groupservice.group.domain.groups;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

/**
 * Interface to perform Reactive operations against the repository's "groups" table.
 * Spring can dependency inject a bean implementing this interface at runtime.
 */
public interface GroupRepository extends ReactiveCrudRepository<Group, Long> {

    @Query("SELECT * FROM groups WHERE status = :status")
    Flux<Group> findGroupsByStatus(GroupStatus status);

    @Query("SELECT * FROM groups")
    Flux<Group> getAllGroups();

}
