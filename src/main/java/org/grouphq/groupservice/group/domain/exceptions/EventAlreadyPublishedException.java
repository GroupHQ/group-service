package org.grouphq.groupservice.group.domain.exceptions;

/**
 * Business exception informing that an action cannot be completed
 * because the event of interest has already been published.
 */
public class EventAlreadyPublishedException extends RuntimeException {
    public EventAlreadyPublishedException(String action) {
        super(action.trim() + " because this event has already been published.");
    }
}
