package com.grouphq.groupservice.group.domain.exceptions;

/**
 * Business exception informing that an action cannot be completed
 * because the group of interest is full.
 */
public class GroupIsFullException extends RuntimeException {
    public GroupIsFullException(String action) {
        super(action.trim() + " because this group is full.");
    }
}
