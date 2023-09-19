package org.grouphq.groupservice.group.domain.outbox;

import org.grouphq.groupservice.group.domain.outbox.enums.AggregateType;
import org.grouphq.groupservice.group.domain.outbox.enums.EventStatus;
import org.grouphq.groupservice.group.domain.outbox.enums.EventType;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Interface to perform Reactive operations against the repository's "outbox" table.
 */
public interface OutboxRepository extends ReactiveCrudRepository<OutboxEvent, UUID> {

    @Query("SELECT * FROM outbox ORDER BY created_date")
    Flux<OutboxEvent> findAllOrderByCreatedDateAsc();

    @Query("INSERT INTO outbox "
           + "(event_id, aggregate_id, aggregate_type, event_type, event_data, "
           + "event_status, websocket_id, created_date) "
           + "VALUES "
           + "(:eventId, :aggregateId, :aggregateType, :eventType, CAST(:eventData AS JSON),"
           + ":eventStatus, :websocketId, :createdDate)")
    Mono<Void> save(
        @Param("eventId") UUID eventId,
        @Param("aggregateId") Long aggregateId,
        @Param("aggregateType") AggregateType aggregateType,
        @Param("eventType") EventType eventType,
        @Param("eventData") String eventData,  // assuming the event data is serialized to a string
        @Param("eventStatus") EventStatus eventStatus,
        @Param("websocketId") String websocketId,
        @Param("createdDate") Instant createdDate
    );
}
