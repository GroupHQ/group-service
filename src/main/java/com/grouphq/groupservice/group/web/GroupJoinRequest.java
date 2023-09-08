package com.grouphq.groupservice.group.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.Length;

/**
 * A data-access-object representing the type of data expected to be
 * received from users who want to join a group.
 *
 * @param username the user's username
 * @param groupId the ID of the group the user wants to join
 */
public record GroupJoinRequest(
    @NotBlank(message = "Username must be provided and not blank")
    @Length(min = 2, max = 255, message = "Username must be of appropriate length")
    String username,

    @NotNull(message = "Group ID must be provided")
    @Positive(message = "Group ID must be a positive value")
    Long groupId
) {
}
