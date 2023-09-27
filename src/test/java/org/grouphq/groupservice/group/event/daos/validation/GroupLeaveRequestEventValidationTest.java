package org.grouphq.groupservice.group.event.daos.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.grouphq.groupservice.group.event.daos.GroupLeaveRequestEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("UnitTest")
class GroupLeaveRequestEventValidationTest {

    private static final String WEBSOCKET_ID = "websocketId";
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("Validation succeeds when all fields valid")
    void whenAllFieldsCorrectThenValidationSucceeds() {
        final var requestEvent = new GroupLeaveRequestEvent(
            UUID.randomUUID(), 1L, 1L,
            WEBSOCKET_ID, Instant.now());
        final Set<ConstraintViolation<GroupLeaveRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Validation fails when memberId is null")
    void whenMemberIdIsNullThenValidationFails() {
        final var requestEvent = new GroupLeaveRequestEvent(
            UUID.randomUUID(), 1L, null,
            WEBSOCKET_ID, Instant.now());
        final Set<ConstraintViolation<GroupLeaveRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Member ID must be provided");
    }

    @Test
    @DisplayName("Validation fails when memberId is negative")
    void whenMemberIdIsNegativeThenValidationFails() {
        final var requestEvent = new GroupLeaveRequestEvent(
            UUID.randomUUID(), 1L, -1L,
            WEBSOCKET_ID, Instant.now());
        final Set<ConstraintViolation<GroupLeaveRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Member ID must be a positive value");
    }

    @Test
    @DisplayName("Validation fails when memberId is zero")
    void whenMemberIdIsZeroThenValidationFails() {
        final var requestEvent = new GroupLeaveRequestEvent(
            UUID.randomUUID(), 1L, 0L,
            WEBSOCKET_ID, Instant.now());
        final Set<ConstraintViolation<GroupLeaveRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Member ID must be a positive value");
    }
}
