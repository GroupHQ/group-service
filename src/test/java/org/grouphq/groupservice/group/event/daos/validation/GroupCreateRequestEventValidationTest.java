package org.grouphq.groupservice.group.event.daos.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.grouphq.groupservice.group.event.daos.GroupCreateRequestEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("UnitTest")
class GroupCreateRequestEventValidationTest {

    private static final String TITLE = "Title";
    private static final String DESCRIPTION = "Description";
    private static final String SYSTEM = "system";
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
        final var requestEvent = new GroupCreateRequestEvent(UUID.randomUUID(), TITLE,
            DESCRIPTION, 10, 5, SYSTEM,
            null, Instant.now());
        final Set<ConstraintViolation<GroupCreateRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Validation fails when eventId is null")
    void whenEventIdIsNullThenValidationFails() {
        final var requestEvent = new GroupCreateRequestEvent(null, TITLE,
            DESCRIPTION, 10, 5, SYSTEM,
            null, Instant.now());
        final Set<ConstraintViolation<GroupCreateRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Event ID must be provided");
    }

    @Test
    @DisplayName("Validation fails when createdDate is null")
    void whenCreatedDateIsNullThenValidationFails() {
        final var requestEvent = new GroupCreateRequestEvent(UUID.randomUUID(), TITLE,
            DESCRIPTION, 10, 5, SYSTEM,
            null, null);
        final Set<ConstraintViolation<GroupCreateRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Created date must be provided");
    }

    @Test
    @DisplayName("Validation fails when created date is in the future")
    void whenCreatedDateIsInPastThenValidationFails() {
        final var requestEvent = new GroupCreateRequestEvent(UUID.randomUUID(), TITLE,
            DESCRIPTION, 10, 5, SYSTEM,
            null, Instant.now().plusSeconds(5));
        final Set<ConstraintViolation<GroupCreateRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo(
            "Created date must be in the past or present");
    }

    @Test
    @DisplayName("Validation fails when title is null")
    void whenTitleIsNullThenValidationFails() {
        final var requestEvent = new GroupCreateRequestEvent(UUID.randomUUID(), null,
            DESCRIPTION, 10, 5, SYSTEM,
            null, Instant.now());
        final Set<ConstraintViolation<GroupCreateRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo(
            "Title must be provided and not blank");
    }

    @Test
    @DisplayName("Validation fails when title is blank")
    void whenTitleIsBlankThenValidationFails() {
        final var requestEvent = new GroupCreateRequestEvent(UUID.randomUUID(), "",
            DESCRIPTION, 10, 5, SYSTEM,
            null, Instant.now());
        final Set<ConstraintViolation<GroupCreateRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(2);
        assertThat(violations).extracting(ConstraintViolation::getMessage)
            .containsExactlyInAnyOrder(
                "Title must be at least 2 characters and no more than 255 characters",
                "Title must be provided and not blank");
    }

    @Test
    @DisplayName("Validation fails when title length is greater than maximum")
    void whenTitleIsGreaterThanMaxThenValidationFails() {
        final var requestEvent = new GroupCreateRequestEvent(UUID.randomUUID(),
            "a".repeat(256), DESCRIPTION, 10, 5, SYSTEM,
            null, Instant.now());
        final Set<ConstraintViolation<GroupCreateRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo(
            "Title must be at least 2 characters and no more than 255 characters");
    }

    @Test
    @DisplayName("Validation fails when title length is less than minimum")
    void whenTitleIsLessThanMinThenValidationFails() {
        final var requestEvent = new GroupCreateRequestEvent(UUID.randomUUID(), "a",
            DESCRIPTION, 10, 5, SYSTEM,
            null, Instant.now());
        final Set<ConstraintViolation<GroupCreateRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo(
            "Title must be at least 2 characters and no more than 255 characters");
    }

    @Test
    @DisplayName("Validation fails when description length is greater than maximum")
    void whenDescriptionIsGreaterThanMaxThenValidationFails() {
        final var requestEvent = new GroupCreateRequestEvent(UUID.randomUUID(), TITLE,
            "a".repeat(2049), 10, 5, SYSTEM,
            null, Instant.now());
        final Set<ConstraintViolation<GroupCreateRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Description must be no more than 2000 characters");
    }

    @Test
    @DisplayName("Validation fails when maxGroupSize is negative")
    void whenMaxGroupSizeIsNegativeOrZeroThenValidationFails() {
        final var requestEvent = new GroupCreateRequestEvent(UUID.randomUUID(), TITLE,
            DESCRIPTION, -1, 5, SYSTEM,
            null, Instant.now());
        final Set<ConstraintViolation<GroupCreateRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Max group size must be a positive value");
    }

    @Test
    @DisplayName("Validation fails when maxGroupSize is zero")
    void whenMaxGroupSizeIsZeroThenValidationFails() {
        final var requestEvent = new GroupCreateRequestEvent(UUID.randomUUID(), TITLE,
            DESCRIPTION, 0, 5, SYSTEM,
            null, Instant.now());
        final Set<ConstraintViolation<GroupCreateRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo(
            "Max group size must be a positive value");
    }

    @Test
    @DisplayName("Validation fails when currentGroupSize is negative")
    void whenCurrentGroupSizeIsNegativeThenValidationFails() {
        final var requestEvent = new GroupCreateRequestEvent(UUID.randomUUID(), TITLE,
            DESCRIPTION, 10, -1, SYSTEM,
            null, Instant.now());
        final Set<ConstraintViolation<GroupCreateRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Current group size must be a positive value (or 0)");
    }

    @Test
    @DisplayName("Validation fails when createdBy is null")
    void whenCreatedByIsNullThenValidationFails() {
        final var requestEvent = new GroupCreateRequestEvent(UUID.randomUUID(), TITLE,
            DESCRIPTION, 10, 5, null, null, Instant.now());
        final Set<ConstraintViolation<GroupCreateRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo(
            "Created by must be provided and not blank");
    }

    @Test
    @DisplayName("Validation fails when createdBy is blank")
    void whenCreatedByIsBlankThenValidationFails() {
        final var requestEvent = new GroupCreateRequestEvent(UUID.randomUUID(), TITLE,
            DESCRIPTION, 10, 5, "", null, Instant.now());
        final Set<ConstraintViolation<GroupCreateRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(2);
        assertThat(violations).extracting(ConstraintViolation::getMessage)
            .containsExactlyInAnyOrder(
                "Created by must be provided and not blank",
                "Created by must be at least 2 characters and no more than 64 characters");
    }

    @Test
    @DisplayName("Validation fails when createdBy length is greater than maximum")
    void whenCreatedByIsGreaterThanMaxThenValidationFails() {
        final var requestEvent = new GroupCreateRequestEvent(UUID.randomUUID(), TITLE,
            DESCRIPTION, 10, 5, "a".repeat(65),
            null, Instant.now());
        final Set<ConstraintViolation<GroupCreateRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Created by must be at least 2 characters and no more than 64 characters");
    }

    @Test
    @DisplayName("Validation fails when createdBy length is less than minimum")
    void whenCreatedByIsLessThanMinThenValidationFails() {
        final var requestEvent = new GroupCreateRequestEvent(UUID.randomUUID(), TITLE,
            DESCRIPTION, 10, 5, "a",
            null, Instant.now());
        final Set<ConstraintViolation<GroupCreateRequestEvent>> violations =
            validator.validate(requestEvent);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Created by must be at least 2 characters and no more than 64 characters");
    }
}
