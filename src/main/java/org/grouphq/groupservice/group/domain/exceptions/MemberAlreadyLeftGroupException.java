package org.grouphq.groupservice.group.domain.exceptions;

import org.grouphq.groupservice.group.domain.members.Member;

/**
 * Exception thrown when a member tries to leave a group they have already left.
 */
public class MemberAlreadyLeftGroupException extends RuntimeException {

    public final Member member;

    public MemberAlreadyLeftGroupException(Member member) {
        super("Cannot leave group because the user has already left the group");
        this.member = member;
    }
}
