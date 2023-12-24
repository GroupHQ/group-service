package org.grouphq.groupservice.group.domain.groups.repository;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import reactor.core.publisher.Mono;

/**
 * Contains methods for performing updates against the repository's "groups" table.
 * R2dbcEntityTemplate is a wrapper around the R2dbcOperations interface that allows
 * query results to be mapped to entities. It's needed to trigger Spring Data R2DBC
 * to update fields it manages such as last_modified_date and version.
 * Using direct queries will not trigger Spring Data R2DBC to update these fields.
 * This includes queries using @Query annotations and DatabaseClient.
 */
@RequiredArgsConstructor
public class GroupUpdatesRepositoryImpl implements GroupUpdatesRepository {

    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    @Override
    public Mono<Group> updateStatusByGroupId(Long groupId, GroupStatus status) {
        return r2dbcEntityTemplate.select(Group.class)
            .matching(query(where("id").is(groupId)))
            .one()
            .map(group -> group.withStatus(status))
            .flatMap(r2dbcEntityTemplate::update);
    }

    @Override
    public Mono<Group> updatedLastMemberActivityByGroupId(Long groupId, Instant lastMemberActivity) {
        return r2dbcEntityTemplate.select(Group.class)
            .matching(query(where("id").is(groupId)))
            .one()
            .map(group -> group.withLastMemberActivity(lastMemberActivity))
            .flatMap(r2dbcEntityTemplate::update);
    }
}
