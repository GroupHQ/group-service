package com.grouphq.groupservice.group.web.objects;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

@JsonTest
@Tag("UnitTest")
class GroupLeaveRequestJsonTest {

    @Autowired
    private JacksonTester<GroupLeaveRequest> json;

    @Test
    @DisplayName("Correctly deserializes data to GroupLeaveRequest object")
    void deserializeToObject() throws IOException {
        final String content = """
            {
                "memberId": 1234
            }
            """;
        assertThat(json.parse(content))
            .usingRecursiveComparison().isEqualTo(new GroupLeaveRequest(1234L));
    }
}
