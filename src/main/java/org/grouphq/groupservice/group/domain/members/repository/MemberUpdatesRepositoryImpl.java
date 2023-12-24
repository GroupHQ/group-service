package org.grouphq.groupservice.group.domain.members.repository;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.grouphq.groupservice.group.domain.members.Member;
import org.grouphq.groupservice.group.domain.members.MemberStatus;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Contains methods for performing updates against the repository's "members" table.
 * R2dbcEntityTemplate is a wrapper around the R2dbcOperations interface that allows
 * query results to be mapped to entities. It's needed to trigger Spring Data R2DBC
 * to update fields it manages such as last_modified_date and version.
 * Using direct queries will not trigger Spring Data R2DBC to update these fields.
 * This includes queries using @Query annotations and DatabaseClient.
 */
@RequiredArgsConstructor
public class MemberUpdatesRepositoryImpl implements MemberUpdatesRepository {

    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    @Override
    public Mono<Member> removeMemberFromGroup(Long memberId, UUID websocketId, MemberStatus status) {
        return Mono.defer(() -> {
            // Check if the status is ACTIVE and return an error Mono if it is
            if (status == MemberStatus.ACTIVE) {
                return Mono.error(new IllegalArgumentException(
                    "A member will not be considered removed with an ACTIVE status"));
            }

            // Proceed with the normal logic if the status is not ACTIVE
            return r2dbcEntityTemplate.select(Member.class)
                .matching(query(where("id").is(memberId).and("websocket_id").is(websocketId)))
                .one()
                .map(member -> member.withStatus(status).withExitedDate(Instant.now()))
                .flatMap(r2dbcEntityTemplate::update);
        });
    }

    @Override
    public Flux<Member> autoDisbandActiveMembers(Long groupId) {
        return r2dbcEntityTemplate.select(Member.class)
            .matching(query(where("group_id").is(groupId)
                .and("member_status").is(MemberStatus.ACTIVE)))
            .all()
            .map(member -> member.withStatus(MemberStatus.AUTO_LEFT).withExitedDate(Instant.now()))
            .flatMap(r2dbcEntityTemplate::update);
    }
}
