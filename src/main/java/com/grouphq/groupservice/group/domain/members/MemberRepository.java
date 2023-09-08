package com.grouphq.groupservice.group.domain.members;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

/**
 * Interface to perform Reactive operations against the repository's "members" table.
 * Spring can dependency inject a bean implementing this interface at runtime.
 */
public interface MemberRepository extends ReactiveCrudRepository<Member, Long> {

    @Query("SELECT * FROM members WHERE group_id = :id")
    Flux<Member> getMembersByGroup(Long id);

    @Query("SELECT * FROM members WHERE group_id = :id AND member_status = 'ACTIVE'")
    Flux<Member> getActiveMembersByGroup(Long id);
}
