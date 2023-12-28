package org.grouphq.groupservice.group.event;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grouphq.groupservice.group.domain.outbox.OutboxEvent;
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

    @PollableBean
    public Supplier<Flux<OutboxEvent>> processedEvents() {
        return () -> outboxService.getOutboxEvents()
            .limitRate(10)
            .flatMap(outboxEvent -> outboxService.deleteEvent(outboxEvent).thenReturn(outboxEvent)
                .doOnNext(event ->  log.info("Publishing event: {}", outboxEvent))
                .onErrorResume(throwable -> {
                    log.error("Error publishing event: {}", outboxEvent, throwable);
                    return Mono.empty();
                }))
            .onErrorResume(throwable -> Mono.empty());
    }
}
