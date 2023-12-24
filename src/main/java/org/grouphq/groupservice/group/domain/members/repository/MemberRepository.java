package org.grouphq.groupservice.group.domain.members.repository;

import java.util.UUID;
import org.grouphq.groupservice.group.domain.members.Member;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Interface to perform Reactive operations against the repository's "members" table.
 * Spring dependency injects a bean implementing this interface at runtime.
 */
public interface MemberRepository
    extends ReactiveCrudRepository<Member, Long>, MemberUpdatesRepository {

    @Query("SELECT * FROM members WHERE group_id = :id")
    Flux<Member> getMembersByGroup(Long id);

    @Query("SELECT * FROM members WHERE group_id = :id "
           + "AND member_status = 'ACTIVE' ORDER BY created_date")
    Flux<Member> getActiveMembersByGroup(Long id);

    @Query("SELECT * FROM members WHERE id = :id AND websocket_id = :websocketId")
    Mono<Member> findMemberByIdAndWebsocketId(Long id, UUID websocketId);
}
