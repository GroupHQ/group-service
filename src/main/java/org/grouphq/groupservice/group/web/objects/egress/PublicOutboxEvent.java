package org.grouphq.groupservice.group.web.objects.egress;

import java.time.Instant;
import org.grouphq.groupservice.group.domain.outbox.EventDataModel;
import org.grouphq.groupservice.group.domain.outbox.enums.AggregateType;
import org.grouphq.groupservice.group.domain.outbox.enums.EventStatus;
import org.grouphq.groupservice.group.domain.outbox.enums.EventType;

/**
 * A data-access-object representing an outbox event containing
 * only necessary and insensitive attributes for clients.
 *
 * @param aggregateId The ID of the aggregate that the event is for
 * @param aggregateType The type of aggregate that the event is for
 * @param eventType The type of event
 * @param eventData The data of the event
 * @param eventStatus The status of the event
 * @param createdDate The date the event was created
 */
public record PublicOutboxEvent(Long aggregateId, AggregateType aggregateType,
                                EventType eventType, EventDataModel eventData,
                                EventStatus eventStatus, Instant createdDate) {
}