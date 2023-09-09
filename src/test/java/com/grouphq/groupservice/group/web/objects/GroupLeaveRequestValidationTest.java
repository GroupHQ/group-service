package com.grouphq.groupservice.group.web.objects;

import static org.assertj.core.api.Assertions.assertThat;

import com.grouphq.groupservice.group.web.GroupLeaveRequest;
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
class GroupLeaveRequestValidationTest {

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
        final var groupLeaveRequest = new GroupLeaveRequest(1L);
        final Set<ConstraintViolation<GroupLeaveRequest>> violations =
            validator.validate(groupLeaveRequest);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Validation fails when member ID is null")
    void whenMemberIdIsNullThenValidationFails() {
        final var groupLeaveRequest = new GroupLeaveRequest(null);
        final Set<ConstraintViolation<GroupLeaveRequest>> violations =
            validator.validate(groupLeaveRequest);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Member ID must be provided");
    }

    @Test
    @DisplayName("Validation fails when member ID is non positive")
    void whenMemberIdIsNotPositiveThenValidationFails() {
        final var groupLeaveRequest = new GroupLeaveRequest(0L);
        final Set<ConstraintViolation<GroupLeaveRequest>> violations =
            validator.validate(groupLeaveRequest);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Member ID must be a positive value");
    }
}
