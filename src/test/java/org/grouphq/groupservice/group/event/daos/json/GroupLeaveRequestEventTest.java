package org.grouphq.groupservice.group.event.daos.json;

import static org.assertj.core.api.Assertions.assertThat;

import org.grouphq.groupservice.group.event.daos.GroupLeaveRequestEvent;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

@JsonTest
@Tag("UnitTest")
class GroupLeaveRequestEventTest {

    @Autowired
    private JacksonTester<GroupLeaveRequestEvent> json;

    @Test
    @DisplayName("Correctly deserializes data to GroupJoinRequestEvent object")
    void deserializeToObject() throws IOException {
        final UUID eventId = UUID.fromString("c7581475-69f3-49b7-bad9-925dea28a77f");
        final Instant timestamp = Instant.parse("2023-09-18T00:00:00.00Z");

        final GroupLeaveRequestEvent event = new GroupLeaveRequestEvent(
            eventId,
            1L,
            1L,
            "websocketId",
            timestamp
        );

        final String content = """
            {
                "eventId": "c7581475-69f3-49b7-bad9-925dea28a77f",
                "aggregateId": 1,
                "memberId": 1,
                "websocketId": "websocketId",
                "createdDate": "2023-09-18T00:00:00.00Z"
            }
            """;

        assertThat(json.parse(content)).usingRecursiveComparison().isEqualTo(event);
    }
}