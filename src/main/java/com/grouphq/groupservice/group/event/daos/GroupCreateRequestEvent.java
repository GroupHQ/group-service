package com.grouphq.groupservice.group.event.daos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.validator.constraints.Length;

/**
 * Data class for the group create request event.
 * <p>This class is used to request the creation of a group.</p>
 */
@Getter
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

    @PositiveOrZero(message = "Current group size must be a positive value (or 0)")
    private final int currentGroupSize;

    @NotBlank(message = "Created by must be provided and not blank")
    @Length(min = 2, max = 64,
        message = "Created by must be at least 2 characters and no more than 64 characters")
    private final String createdBy;

    public GroupCreateRequestEvent(
        UUID eventId,
        String title,
        String description,
        int maxGroupSize,
        int currentGroupSize,
        String createdBy,
        String websocketId,
        Instant createdDate
    ) {
        super(eventId, null, websocketId, createdDate);
        this.title = title;
        this.description = description;
        this.maxGroupSize = maxGroupSize;
        this.currentGroupSize = currentGroupSize;
        this.createdBy = createdBy;
    }
}
