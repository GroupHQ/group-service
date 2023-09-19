package org.grouphq.groupservice.group.event.daos;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.validator.constraints.Length;

/**
 * Data class for the group join request event.
 * <p>This class is used to request a member to join a group.</p>
 */
public class GroupJoinRequestEvent extends RequestEvent {

    @Getter
    @NotBlank(message = "Username must be provided and not blank")
    @Length(min = 2, max = 64, message = "Username must be between 2 and 64 characters")
    private final String username;

    public GroupJoinRequestEvent(UUID eventId, Long groupId, String username,
                                 String websocketId, Instant createdDate) {
        super(eventId, groupId, websocketId, createdDate);
        this.username = username;
    }
}
