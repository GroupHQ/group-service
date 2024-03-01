package org.grouphq.groupservice.group.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grouphq.groupservice.group.domain.outbox.OutboxEvent;
import org.grouphq.groupservice.group.domain.outbox.OutboxEventJson;
import org.grouphq.groupservice.group.domain.outbox.OutboxService;
import org.springframework.cloud.function.context.PollableBean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A class for publishing group events.
 * <p>This class is responsible for publishing group events. It is a Spring Cloud Function
 * that is integrated with Spring Cloud Stream. It is a supplier of group event results.</p>
 */
@Slf4j
@RequiredArgsConstructor
@Configuration
public class GroupEventPublisher {

    private final OutboxService outboxService;

    private final List<OutboxEvent> eventsDeletedTempGroup89 = new CopyOnWriteArrayList<>();

    @PollableBean
    public Supplier<Flux<OutboxEvent>> processedEvents() {
        return () -> outboxService.getOutboxEvents()
            .limitRate(10)
            .flatMap(outboxEvent -> outboxService.deleteEvent(outboxEvent).thenReturn(outboxEvent)
                .doOnNext(event ->  {
                    log.info("Publishing event: {}", outboxEvent);
                    eventsDeletedTempGroup89.add(outboxEvent);
                })
                .onErrorResume(throwable -> {
                    log.error("Error publishing event: {}", outboxEvent, throwable);
                    return Mono.empty();
                }))
            // .doOnError(error -> log.error("Could not send outbox event", error))
            // The above log pollutes tests in-between Testcontainers lifecycle due to this being a pollable bean
            // TODO: Find an alternative
            .onErrorResume(throwable -> Mono.empty());
    }

    /**
     * Temporary auxiliary publisher to migrate clients to migrate away from OutboxEvent to OutboxEventJson.
     * Once this migration is complete, the old data model will be updated to match the internal structure
     * of OutboxEventJson. OutboxEventJson will then be discarded after clients migrate back to the newly
     * updated OutboxEvent data model. While this seems like a contrived migration strategy, it allows
     * backward compatible updates to all systems with no downtime.
     * Related to Group-89 issue.
     *
     * @return OutboxEventJson a temporary new object where OutboxEvent will soon become
     */
    @PollableBean
    public Supplier<Flux<OutboxEventJson>> processedEventsTempGroup89() {
        return () -> Flux.fromIterable(eventsDeletedTempGroup89)
            .doOnNext(outboxEvent -> eventsDeletedTempGroup89.clear())
            .flatMap(outboxEvent -> outboxService.deleteEvent(outboxEvent).thenReturn(outboxEvent)
                .doOnNext(event ->  log.info("Publishing event: {}", outboxEvent))
                .onErrorResume(throwable -> {
                    log.error("Error publishing event: {}", outboxEvent, throwable);
                    return Mono.empty();
                }))
            .flatMap(OutboxEventJson::copy)
            //  .doOnError(error -> log.error("Could not send outbox event", error))
            // The above log pollutes tests in-between Testcontainers lifecycle due to this being a pollable bean
            // TODO: Find an alternative
            .onErrorResume(throwable -> Mono.empty());
    }
}
