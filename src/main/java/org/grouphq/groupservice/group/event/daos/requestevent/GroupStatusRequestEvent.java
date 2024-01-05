package org.grouphq.groupservice.group.event.daos.requestevent;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;

/**
 * Data class for the group status request event.
 * <p>This class is used to request a change in the status of a group.</p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
@ToString(callSuper = true)
public class GroupStatusRequestEvent extends RequestEvent {

    @NotNull(message = "New status must be provided")
    private final GroupStatus newStatus;

    public GroupStatusRequestEvent(
        UUID eventId,
        Long groupId,
        GroupStatus newStatus,
        String websocketId,
        Instant createdDate
    ) {
        super(eventId, groupId, websocketId, createdDate);
        this.newStatus = newStatus;
    }
}