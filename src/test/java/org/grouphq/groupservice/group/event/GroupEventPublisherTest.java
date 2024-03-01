package org.grouphq.groupservice.group.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import org.grouphq.groupservice.group.domain.outbox.OutboxEvent;
import org.grouphq.groupservice.group.domain.outbox.OutboxService;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
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
    private OutboxService outboxService;

    @InjectMocks
    private GroupEventPublisher groupEventPublisher;

    @Test
    @DisplayName("Retrieves messages from outbox, deletes them, then supplies them")
    void retrieveDeleteThenPublishMessages() throws JsonProcessingException {
        final OutboxEvent[] outboxEvent = {
            GroupTestUtility.generateOutboxEvent(),
            GroupTestUtility.generateOutboxEvent(),
            GroupTestUtility.generateOutboxEvent(),
        };

        given(outboxService.getOutboxEvents())
            .willReturn(Flux.just(outboxEvent));

        given(outboxService.deleteEvent(outboxEvent[0]))
            .willReturn(Mono.just(outboxEvent[0]));

        given(outboxService.deleteEvent(outboxEvent[1]))
            .willReturn(Mono.just(outboxEvent[1]));

        given(outboxService.deleteEvent(outboxEvent[2]))
            .willReturn(Mono.just(outboxEvent[2]));

        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        StepVerifier.create(groupEventPublisher.processedEvents().get())
            .expectNext(outboxEvent[0])
            .expectNext(outboxEvent[1])
            .expectNext(outboxEvent[2])
            .expectComplete()
            .verify(Duration.ofSeconds(1));

        verify(outboxService).getOutboxEvents();
        verify(outboxService).deleteEvent(outboxEvent[0]);
        verify(outboxService).deleteEvent(outboxEvent[1]);
        verify(outboxService).deleteEvent(outboxEvent[2]);
    }
}
