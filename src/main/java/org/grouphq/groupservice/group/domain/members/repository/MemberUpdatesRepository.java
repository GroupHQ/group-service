package org.grouphq.groupservice.group.domain.members.repository;

import java.util.UUID;
import org.grouphq.groupservice.group.domain.members.Member;
import org.grouphq.groupservice.group.domain.members.MemberStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Interface to perform Reactive update operations against the repository's "members" table.
 */
public interface MemberUpdatesRepository {
    Mono<Member> removeMemberFromGroup(Long memberId, UUID websocketId, MemberStatus status);

    Flux<Member> autoDisbandActiveMembers(Long groupId);
}
