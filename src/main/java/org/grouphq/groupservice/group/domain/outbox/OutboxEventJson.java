package org.grouphq.groupservice.group.domain.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grouphq.groupservice.group.domain.outbox.enums.AggregateType;
import org.grouphq.groupservice.group.domain.outbox.enums.EventStatus;
import org.grouphq.groupservice.group.domain.outbox.enums.EventType;
import reactor.core.publisher.Mono;

/**
 * A type of OutboxEvent with its event data serialized to the appropriate object.
 * This variation of OutboxEvent is used when sending events to clients, simplifying the steps
 * needed for them to read received events. Ideally, we would use this type of class for both
 * sending to clients and saving to the database, but the current database integration
 * via Spring Data doesn't allow objects to be saved directly--they must first be converted to
 * a string when saving to the database. When retrieving from the database, they should be
 * converted to this object before sending to clients.
 *
 * @see OutboxEvent
 * @since 2/29/2024
 */
@RequiredArgsConstructor
@Data
@Slf4j
public class OutboxEventJson {
    private final UUID eventId;
    private final Long aggregateId;
    private final String websocketId;
    private final AggregateType aggregateType;
    private final EventType eventType;
    private final EventDataModel eventData;
    private final EventStatus eventStatus;
    private final Instant createdDate;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
    }

    public static Mono<OutboxEventJson> copy(OutboxEvent outboxEvent) {
        try {
            final EventDataModel eventDataModel =
                OBJECT_MAPPER.readValue(outboxEvent.getEventData(), EventDataModel.class);
            return Mono.just(copy(outboxEvent, eventDataModel));
        } catch (JsonProcessingException jsonProcessingException) {
            log.error("Could not convert event data from string to EventDataModel for event {}",
                outboxEvent, jsonProcessingException);
            return Mono.error(jsonProcessingException);
        }
    }

    public static OutboxEventJson copy(OutboxEvent outboxEvent, EventDataModel eventDataModel) {
        return new OutboxEventJson(
            outboxEvent.getEventId(),
            outboxEvent.getAggregateId(),
            outboxEvent.getWebsocketId(),
            outboxEvent.getAggregateType(),
            outboxEvent.getEventType(),
            eventDataModel,
            outboxEvent.getEventStatus(),
            outboxEvent.getCreatedDate()
        );
    }
}