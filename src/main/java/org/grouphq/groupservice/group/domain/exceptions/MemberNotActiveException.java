package org.grouphq.groupservice.group.domain.exceptions;

/**
 * Business exception informing that an action cannot be completed
 * because the member's status is not active.
 */
public class MemberNotActiveException extends RuntimeException {
    public MemberNotActiveException(String action) {
        super(action.trim() + " because this member's status is not active");
    }
}
