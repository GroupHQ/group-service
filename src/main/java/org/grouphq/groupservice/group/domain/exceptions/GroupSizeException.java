package org.grouphq.groupservice.group.domain.exceptions;

/**
 * Business exception informing that an action cannot be completed
 * because the group of interest's proposed size exceeds its maximum size.
 */
public class GroupSizeException extends RuntimeException {
    public GroupSizeException(String action) {
        super(action.trim() + " because this group's proposed size exceeds its maximum size.");
    }
}
