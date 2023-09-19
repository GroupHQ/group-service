package com.grouphq.groupservice.group.event.daos.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.grouphq.groupservice.group.event.daos.GroupJoinRequestEvent;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("UnitTest")
class GroupJoinRequestEventValidationTest {

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
        final var requestEvent = new GroupJoinRequestEvent(
            UUID.randomUUID(), 1L, "User",
            WEBSOCKET_ID, Instant.now());
        final Set<ConstraintViolation<GroupJoinRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Validation fails when username is null")
    void whenUsernameIsNullThenValidationFails() {
        final var requestEvent = new GroupJoinRequestEvent(
            UUID.randomUUID(), 1L, null,
            WEBSOCKET_ID, Instant.now());
        final Set<ConstraintViolation<GroupJoinRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Username must be provided and not blank");
    }

    @Test
    @DisplayName("Validation fails when username is blank")
    void whenUsernameIsBlankThenValidationFails() {
        final var requestEvent = new GroupJoinRequestEvent(
            UUID.randomUUID(), 1L, "",
            WEBSOCKET_ID, Instant.now());
        final Set<ConstraintViolation<GroupJoinRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(2);

        final Set<String> expectedViolations = Set.of(
            "Username must be between 2 and 64 characters",
            "Username must be provided and not blank"
        );

        assertThat(violations).extracting(ConstraintViolation::getMessage)
            .containsExactlyInAnyOrderElementsOf(expectedViolations);
    }

    @Test
    @DisplayName("Validation fails when username is too short")
    void whenUsernameIsTooShortThenValidationFails() {
        final var requestEvent = new GroupJoinRequestEvent(
            UUID.randomUUID(), 1L, "a",
            WEBSOCKET_ID, Instant.now());
        final Set<ConstraintViolation<GroupJoinRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Username must be between 2 and 64 characters");
    }

    @Test
    @DisplayName("Validation fails when username is too long")
    void whenUsernameIsTooLongThenValidationFails() {
        final var requestEvent = new GroupJoinRequestEvent(
            UUID.randomUUID(), 1L, "a".repeat(65),
            WEBSOCKET_ID, Instant.now());
        final Set<ConstraintViolation<GroupJoinRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Username must be between 2 and 64 characters");
    }
}
