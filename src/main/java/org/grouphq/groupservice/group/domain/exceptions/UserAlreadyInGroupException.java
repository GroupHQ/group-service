package org.grouphq.groupservice.group.domain.exceptions;

public class UserAlreadyInGroupException extends RuntimeException {
    public UserAlreadyInGroupException(String action) {
        super(action + " because the user has an active member in some group");
    }
}
