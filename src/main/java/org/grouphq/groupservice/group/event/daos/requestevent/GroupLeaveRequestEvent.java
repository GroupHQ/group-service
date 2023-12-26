package org.grouphq.groupservice.group.event.daos.requestevent;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Data class for the group leave request event.
 * <p>This class is used to request a member to leave a group.</p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class GroupLeaveRequestEvent extends RequestEvent {

    @NotNull(message = "Member ID must be provided")
    @Positive(message = "Member ID must be a positive value")
    private final Long memberId;

    public GroupLeaveRequestEvent(
        UUID eventId,
        Long groupId,
        Long memberId,
        String websocketId,
        Instant createdDate
    ) {
        super(eventId, groupId, websocketId, createdDate);
        this.memberId = memberId;
    }
}
