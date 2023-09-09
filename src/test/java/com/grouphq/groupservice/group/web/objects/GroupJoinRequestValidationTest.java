package com.grouphq.groupservice.group.web.objects;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javafaker.Faker;
import com.grouphq.groupservice.group.web.GroupJoinRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("UnitTest")
class GroupJoinRequestValidationTest {

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
        final var groupJoinRequest = new GroupJoinRequest("User", 1L);
        final Set<ConstraintViolation<GroupJoinRequest>> violations =
            validator.validate(groupJoinRequest);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Validation fails when username is null")
    void whenUsernameIsNullThenValidationFails() {
        final var groupJoinRequest = new GroupJoinRequest(null, 1L);
        final Set<ConstraintViolation<GroupJoinRequest>> violations =
            validator.validate(groupJoinRequest);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Username must be provided and not blank");
    }

    @Test
    @DisplayName("Validation fails when username is blank")
    void whenUsernameIsBlankThenValidationFails() {
        final var groupJoinRequest = new GroupJoinRequest("", 1L);
        final Set<ConstraintViolation<GroupJoinRequest>> violations =
            validator.validate(groupJoinRequest);
        assertThat(violations).hasSize(2);

        final Set<String> expectedViolations = Set.of(
            "Username must be of appropriate length",
            "Username must be provided and not blank"
        );

        for (final var violation : violations) {
            assertThat(expectedViolations).contains(violation.getMessage());
        }
    }

    @Test
    @DisplayName("Validation fails when username length is less than minimum")
    void whenUsernameIsLessThanMinThenValidationFails() {
        final var groupJoinRequest = new GroupJoinRequest("Z", 1L);
        final Set<ConstraintViolation<GroupJoinRequest>> violations =
            validator.validate(groupJoinRequest);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Username must be of appropriate length");
    }

    @Test
    @DisplayName("Validation fails when username length is greater than maximum")
    void whenUsernameIsGreaterThanMaxThenValidationFails() {
        final Faker faker = new Faker();
        final var groupJoinRequest = new GroupJoinRequest(faker.lorem().characters(256), 1L);
        final Set<ConstraintViolation<GroupJoinRequest>> violations =
            validator.validate(groupJoinRequest);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Username must be of appropriate length");
    }

    @Test
    @DisplayName("Validation fails when group ID is null")
    void whenGroupIdIsNullThenValidationFails() {
        final var groupJoinRequest = new GroupJoinRequest("Bo", null);
        final Set<ConstraintViolation<GroupJoinRequest>> violations =
            validator.validate(groupJoinRequest);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Group ID must be provided");
    }

    @Test
    @DisplayName("Validation fails when group ID is non positive")
    void whenGroupIdIsNotPositiveThenValidationFails() {
        final var groupJoinRequest = new GroupJoinRequest("Bo", 0L);
        final Set<ConstraintViolation<GroupJoinRequest>> violations =
            validator.validate(groupJoinRequest);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Group ID must be a positive value");
    }
}
