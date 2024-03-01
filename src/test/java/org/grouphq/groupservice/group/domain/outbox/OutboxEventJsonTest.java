package org.grouphq.groupservice.group.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.grouphq.groupservice.group.domain.members.Member;
import org.grouphq.groupservice.group.testutility.GroupTestUtility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

@Tag("UnitTest")
class OutboxEventJsonTest {

    @Test
    @DisplayName("Converts an OutboxEvent object to OutboxEventJson")
    void convertOutboxEventToJson() {
        final OutboxEvent outboxEvent = GroupTestUtility.generateOutboxEvent();

        StepVerifier.create(OutboxEventJson.copy(outboxEvent))
            .assertNext(outboxEventJson -> {
                assertThat(outboxEventJson.getEventId()).isEqualTo(outboxEvent.getEventId());
                assertThat(outboxEventJson.getAggregateId()).isEqualTo(outboxEvent.getAggregateId());
                assertThat(outboxEventJson.getWebsocketId()).isEqualTo(outboxEvent.getWebsocketId());
                assertThat(outboxEventJson.getAggregateType()).isEqualTo(outboxEvent.getAggregateType());
                assertThat(outboxEventJson.getEventType()).isEqualTo(outboxEvent.getEventType());
                assertThat(outboxEventJson.getEventStatus()).isEqualTo(outboxEvent.getEventStatus());
                assertThat(outboxEventJson.getCreatedDate()).isEqualTo(outboxEvent.getCreatedDate());

                assertThat(outboxEventJson.getEventData()).isInstanceOf(EventDataModel.class);
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Converts an OutboxEvent object to OutboxEventJson with the given EventDataModel")
    void convertOutboxEventToJsonWithGivenEventDataModel() {
        final OutboxEvent outboxEvent = GroupTestUtility.generateOutboxEvent();
        final Member member = GroupTestUtility.generateFullMemberDetails();

        final OutboxEventJson outboxEventJson = OutboxEventJson.copy(outboxEvent, member);

        assertThat(outboxEventJson.getEventData()).isEqualTo(member);
    }
}
