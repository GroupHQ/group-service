package org.grouphq.groupservice.group.event.daos.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.grouphq.groupservice.group.event.daos.GroupStatusRequestEvent;
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
class GroupStatusRequestEventValidationTest {

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
        final var requestEvent = new GroupStatusRequestEvent(
            UUID.randomUUID(), 1L, GroupStatus.DISBANDED,
            "websocketId", Instant.now());
        final Set<ConstraintViolation<GroupStatusRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Validation fails when newStatus is null")
    void whenNewStatusIsNullThenValidationFails() {
        final var requestEvent = new GroupStatusRequestEvent(
            UUID.randomUUID(), 1L, null,
            "websocketId", Instant.now());
        final Set<ConstraintViolation<GroupStatusRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("New status must be provided");
    }
}
