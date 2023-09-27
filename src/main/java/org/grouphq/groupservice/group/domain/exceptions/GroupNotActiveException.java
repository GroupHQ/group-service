package org.grouphq.groupservice.group.domain.exceptions;

/**
 * Business exception informing that an action cannot be completed
 * because the group of interest is not active.
 */
public class GroupNotActiveException extends RuntimeException {
    public GroupNotActiveException(String action) {
        super(action.trim() + " because this group is not active.");
    }
}
