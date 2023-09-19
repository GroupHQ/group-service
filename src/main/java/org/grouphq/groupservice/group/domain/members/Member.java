package org.grouphq.groupservice.group.domain.members;

import java.time.Instant;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A member model.
 *
 * @param id A unique ID belonging to a member
 * @param username Member's username
 * @param groupId Group ID identifying the group the member belongs to
 * @param joinedDate Time user joined the group. Same time as createdDate
 * @param exitedDate Time user left the group. Initially null.
 * @param createdDate Time group was created
 * @param lastModifiedDate Time group was last modified / updated
 * @param createdBy Creator of group (e.g. system or user)
 * @param lastModifiedBy Who last modified the group
 * @param version Unique number on group state (used by Spring Data for optimistic locking)
 */
@Table("members")
public record Member(

    @Id
    Long id,
    String username,
    Long groupId,
    MemberStatus memberStatus,

    @CreatedDate
    Instant joinedDate,

    Instant exitedDate,

    @CreatedDate
    Instant createdDate,

    @LastModifiedDate
    Instant lastModifiedDate,

    @CreatedBy
    String createdBy,

    @LastModifiedBy
    String lastModifiedBy,

    @Version
    int version
) {
    public static Member of(String username, Long groupId) {
        return new Member(null, username, groupId, MemberStatus.ACTIVE, null, null,
            null, null, null, null, 0);
    }
}
