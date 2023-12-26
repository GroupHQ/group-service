package org.grouphq.groupservice.group.event.daos;

import java.time.Instant;
import java.util.UUID;
import lombok.EqualsAndHashCode;

/**
 * Data class for the group updated event.
 * Created by the application when a group is updated.
 */
@EqualsAndHashCode(callSuper = true)
public class GroupUpdatedEvent extends Event {
    public GroupUpdatedEvent(Long groupId) {
        super(UUID.randomUUID(), groupId, Instant.now());
    }
}
