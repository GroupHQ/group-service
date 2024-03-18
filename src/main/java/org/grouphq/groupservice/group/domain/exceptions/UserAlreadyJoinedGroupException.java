package org.grouphq.groupservice.group.domain.exceptions;

import org.grouphq.groupservice.group.domain.members.Member;

/**
 * Exception thrown when a user attempts to join a group they already joined.
 */
public class UserAlreadyJoinedGroupException extends RuntimeException {

    public final Member member;

    public UserAlreadyJoinedGroupException(Member member) {
        super("Cannot join group because the user has already joined the group");
        this.member = member;
    }
}
