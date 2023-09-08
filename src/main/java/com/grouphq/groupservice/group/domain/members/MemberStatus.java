package com.grouphq.groupservice.group.domain.members;

/**
 * A member's current status in a group.
 */
public enum MemberStatus {
    /**
     * Member is currently part of its referenced group.
     */
    ACTIVE,

    /**
     * Member was part of its referenced group but has since left.
     */
    LEFT,

    /**
     * Member was part of its referenced group but has since left
     * due to the group being disbanded somehow.
     */
    AUTO_LEFT,

    /**
     * Member was part of its referenced group but has since been kicked.
     */
    KICKED,

    /**
     * Member was part of its referenced group but has since been banned.
     */
    BANNED

}
