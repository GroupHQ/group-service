package org.grouphq.groupservice.group.domain.outbox;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.grouphq.groupservice.group.domain.outbox.enums.AggregateType;
import org.grouphq.groupservice.group.domain.outbox.enums.EventStatus;
import org.grouphq.groupservice.group.domain.outbox.enums.EventType;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Model for the outbox table.
 * <p>Note that both the NoArgsConstructor and the AllArgsConstructor are required.
 * The AllArgsConstructor is required for Spring Data to instantiate the object,
 * while the NoArgsConstructor is required for Jackson to deserialize the object.
 * While there is a lombok.anyconstructor.addconstructorproperties property that can
 * be enabled (it's disabled by default), the property won't apply for some reason.
 * Otherwise, we could just have the @AllArgsConstructor annotation.
 * Until that issue is fixed, we need the @AllArgsConstructor with the @PersistenceCreator
 * annotation to tell Spring Data to use that constructor. We also need the @NoArgsConstructor
 * annotation for Jackson, and the force = true parameter to tell Lombok to instantiate the
 * final fields to their default constructor.</p>
 *
 * @since 9/17/2023
 * @author makmn
 */
@Table("outbox")
@NoArgsConstructor(force = true)
@AllArgsConstructor(onConstructor = @__(@PersistenceCreator))
@Data
public class OutboxEvent {
    @Id
    private final UUID eventId;
    private final Long aggregateId;
    private final String websocketId;
    private final AggregateType aggregateType;
    private final EventType eventType;
    private final String eventData;
    private final EventStatus eventStatus;
    private final Instant createdDate;

    public static OutboxEvent of(UUID eventId, Long aggregateId, AggregateType aggregateType,
                                 EventType eventType, String eventData, EventStatus eventStatus,
                                 String websocketId) {
        return new OutboxEvent(eventId, aggregateId, websocketId, aggregateType,
            eventType, eventData, eventStatus, Instant.now());
    }

}