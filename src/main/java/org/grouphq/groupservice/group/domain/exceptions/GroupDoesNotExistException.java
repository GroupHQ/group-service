package org.grouphq.groupservice.group.domain.exceptions;

/**
 * Business exception informing that an action cannot be completed
 * because the group of interest does not exist.
 */
public class GroupDoesNotExistException extends RuntimeException {
    public GroupDoesNotExistException(String action) {
        super(action.trim() + " because this group does not exist.");
    }
}
