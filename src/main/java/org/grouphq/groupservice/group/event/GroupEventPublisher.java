package org.grouphq.groupservice.group.event;

import java.util.function.Supplier;
import org.grouphq.groupservice.group.domain.outbox.OutboxEvent;
import org.grouphq.groupservice.group.domain.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.function.context.PollableBean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A class for publishing group events.
 * <p>This class is responsible for publishing group events. It is a Spring Cloud Function
 * that is integrated with Spring Cloud Stream. It is a supplier of group event results.</p>
 */
@Configuration
public class GroupEventPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(GroupEventPublisher.class);

    private final OutboxService outboxService;

    public GroupEventPublisher(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @PollableBean
    public Supplier<Flux<OutboxEvent>> processedEvents() {
        return () -> outboxService.getOutboxEvents()
            .limitRate(10)
            .flatMap(outboxEvent -> outboxService.deleteEvent(outboxEvent).thenReturn(outboxEvent)
                .doOnNext(event ->  LOG.debug("Published event: {}", outboxEvent))
                .onErrorResume(throwable -> {
                    LOG.error("Error publishing event: {}", outboxEvent, throwable);
                    return Mono.empty();
                }))
            .doOnError(throwable ->
                LOG.error("""
                    Error received out-of-stream for publisher supplier!
                    Suspecting database connection error.
                    Attempting to resume stream.""",
                    throwable))
            .onErrorResume(throwable -> Mono.empty());
    }
}
