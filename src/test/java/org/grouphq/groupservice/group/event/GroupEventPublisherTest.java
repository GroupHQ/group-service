package org.grouphq.groupservice.group.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.grouphq.groupservice.group.domain.outbox.OutboxEvent;
import org.grouphq.groupservice.group.domain.outbox.OutboxRepository;
import org.grouphq.groupservice.group.domain.outbox.OutboxService;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Tag("UnitTest")
@ExtendWith(MockitoExtension.class)
class GroupEventPublisherTest {
    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private GroupEventPublisher groupEventPublisher;

    @Test
    @DisplayName("Retrieves messages from outbox, deletes them, then supplies them")
    void retrieveDeleteThenPublishMessages() {
        final OutboxEvent[] outboxEvent = {
            GroupTestUtility.generateOutboxEvent(),
            GroupTestUtility.generateOutboxEvent(),
            GroupTestUtility.generateOutboxEvent(),
        };

        given(outboxRepository.findAllOrderByCreatedDateAsc())
            .willReturn(Flux.just(outboxEvent));

        given(outboxService.deleteEvent(outboxEvent[0]))
            .willReturn(Mono.just(outboxEvent[0]));

        given(outboxService.deleteEvent(outboxEvent[1]))
            .willReturn(Mono.just(outboxEvent[1]));

        given(outboxService.deleteEvent(outboxEvent[2]))
            .willReturn(Mono.just(outboxEvent[2]));

        StepVerifier.create(groupEventPublisher.processedEvents().get())
            .expectNext(outboxEvent[0])
            .expectNext(outboxEvent[1])
            .expectNext(outboxEvent[2])
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(outboxRepository).findAllOrderByCreatedDateAsc();
        verify(outboxService).deleteEvent(outboxEvent[0]);
        verify(outboxService).deleteEvent(outboxEvent[1]);
        verify(outboxService).deleteEvent(outboxEvent[2]);
    }
}
