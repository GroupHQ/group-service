package org.grouphq.groupservice.group.event.daos.requestevent;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.grouphq.groupservice.group.event.daos.Event;

/**
 * Data class for the request event.
 * <p>This class is abstract and should be extended by all request event classes.
 * This class contains the common fields for all request events.</p>
 */
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(force = true)
@Data
public abstract class RequestEvent extends Event {

    private final String websocketId;

    public RequestEvent(
        UUID eventId,
        Long aggregateId,
        String websocketId,
        Instant createdDate
    ) {
        super(eventId, aggregateId, createdDate);
        this.websocketId = websocketId;
    }
}
