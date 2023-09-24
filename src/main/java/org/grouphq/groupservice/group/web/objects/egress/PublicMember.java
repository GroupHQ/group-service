package org.grouphq.groupservice.group.web.objects.egress;

import java.time.Instant;
import org.grouphq.groupservice.group.domain.members.MemberStatus;

/**
 * A data-access-object representing a member model containing
 * only necessary and insensitive attributes for client.
 *
 * @param username Member's username
 * @param groupId Group ID identifying the group the member belongs to
 * @param joinedDate Time user joined the group. Same time as createdDate
 * @param exitedDate Time user left the group. Initially null.
 */
public record PublicMember(
    String username,
    Long groupId,
    MemberStatus memberStatus,

    Instant joinedDate,

    Instant exitedDate
) {
}
