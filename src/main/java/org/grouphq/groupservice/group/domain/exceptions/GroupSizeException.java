package org.grouphq.groupservice.group.domain.exceptions;

/**
 * Business exception informing that an action cannot be completed
 * because of an issue related to the group's size.
 */
public class GroupSizeException extends RuntimeException {
    public GroupSizeException(String reason) {
        super(reason.trim());
    }
}
