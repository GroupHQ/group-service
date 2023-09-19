package org.grouphq.groupservice.group.domain.outbox.enums;

/**
 * Enumerates the types of events that can be used in the outbox.
 */
public enum EventType {
    GROUP_CREATED,
    GROUP_STATUS_UPDATED,
    GROUP_DISBANDED,
    MEMBER_JOINED,
    MEMBER_LEFT
}
