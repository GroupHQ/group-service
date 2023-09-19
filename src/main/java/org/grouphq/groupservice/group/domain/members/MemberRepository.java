package org.grouphq.groupservice.group.domain.members;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Interface to perform Reactive operations against the repository's "members" table.
 * Spring can dependency inject a bean implementing this interface at runtime.
 */
public interface MemberRepository extends ReactiveCrudRepository<Member, Long> {

    @Query("SELECT * FROM members WHERE group_id = :id")
    Flux<Member> getMembersByGroup(Long id);

    @Query("SELECT * FROM members WHERE group_id = :id "
           + "AND member_status = 'ACTIVE' ORDER BY joined_date")
    Flux<Member> getActiveMembersByGroup(Long id);

    @Query("UPDATE members SET member_status = 'LEFT' WHERE id = :id")
    Mono<Void> removeMemberFromGroup(Long id);
}
