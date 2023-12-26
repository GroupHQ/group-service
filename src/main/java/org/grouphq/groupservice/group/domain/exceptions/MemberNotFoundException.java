package org.grouphq.groupservice.group.domain.exceptions;

/**
 * Exception thrown when a member is not found in the database or the user does not have
 * appropriate authorization to perform the action.
 */
public class MemberNotFoundException extends RuntimeException {

    public MemberNotFoundException(String action) {
        super(action + " because either the member does not exist "
              + "or you do not have appropriate authorization. "
            + "Make sure you are using the correct member ID and websocket ID.");
    }
}
