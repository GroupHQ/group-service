package org.grouphq.groupservice.group.event.daos.requestevent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.validator.constraints.Length;

/**
 * Data class for the group create request event.
 * <p>This class is used to request the creation of a group.</p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
@ToString(callSuper = true)
public class GroupCreateRequestEvent extends RequestEvent {

    @NotBlank(message = "Title must be provided and not blank")
    @Length(min = 2, max = 255,
        message = "Title must be at least 2 characters and no more than 255 characters")
    private final String title;

    @Length(max = 2048,
        message = "Description must be no more than 2000 characters")
    private final String description;

    @Positive(message = "Max group size must be a positive value")
    private final int maxGroupSize;

    @NotBlank(message = "Created by must be provided and not blank")
    @Length(min = 2, max = 64,
        message = "Created by must be at least 2 characters and no more than 64 characters")
    private final String createdBy;

    public GroupCreateRequestEvent(
        UUID eventId,
        String title,
        String description,
        int maxGroupSize,
        String createdBy,
        String websocketId,
        Instant createdDate
    ) {
        super(eventId, null, websocketId, createdDate);
        this.title = title;
        this.description = description;
        this.maxGroupSize = maxGroupSize;
        this.createdBy = createdBy;
    }
}
