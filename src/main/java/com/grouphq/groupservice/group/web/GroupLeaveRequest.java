package com.grouphq.groupservice.group.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * A data-access-object representing the type of data expected to be
 * received from users who want to leave a group.
 *
 * @param memberId the ID of the user who wants to leave their group
 */
public record GroupLeaveRequest(
    @NotNull(message = "Member ID must be provided")
    @Positive(message = "Member ID must be a positive value")
    Long memberId
) {
}