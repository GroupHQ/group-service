package org.grouphq.groupservice.group.domain.members;

import java.time.Instant;
import java.util.UUID;
import org.grouphq.groupservice.group.web.objects.egress.PublicMember;
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
 * @param websocketId The user's websocket ID for the request
 * @param username Member's username
 * @param groupId Group ID identifying the group the member belongs to
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
    UUID websocketId,

    String username,
    Long groupId,
    MemberStatus memberStatus,

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
        return new Member(null, UUID.randomUUID(), username, groupId, MemberStatus.ACTIVE, null,
            null, null, null, null, 0);
    }

    public static Member of(UUID websocketId, String username, Long groupId) {
        return new Member(null, websocketId, username, groupId,
            MemberStatus.ACTIVE, null, null, null,
            null, null, 0);
    }

    public static Member of(UUID websocketId, String username) {
        return new Member(null, websocketId, username, null,
            MemberStatus.ACTIVE, null, null, null,
            null, null, 0);
    }

    public static Member of(String websocketId, String username, Long groupId) {
        return new Member(null, UUID.fromString(websocketId), username, groupId,
            MemberStatus.ACTIVE, null, null, null,
            null, null, 0);
    }

    public static PublicMember toPublicMember(Member member) {
        return new PublicMember(
            member.id(), member.username(), member.groupId(),
            member.memberStatus(), member.createdDate(), member.exitedDate()
        );
    }

    public Member withStatus(MemberStatus memberStatus) {
        return new Member(
            this.id(),
            this.websocketId(),
            this.username(),
            this.groupId(),
            memberStatus,
            this.exitedDate(),
            this.createdDate(),
            this.lastModifiedDate(),
            this.createdBy(),
            this.lastModifiedBy(),
            this.version()
        );
    }

    public Member withExitedDate(Instant exitedDate) {
        return new Member(
            this.id(),
            this.websocketId(),
            this.username(),
            this.groupId(),
            this.memberStatus(),
            exitedDate,
            this.createdDate(),
            this.lastModifiedDate(),
            this.createdBy(),
            this.lastModifiedBy(),
            this.version()
        );
    }

    public static PublicMember convertMembersToPublicMembers(Member member) {
        return new PublicMember(
            member.id(),
            member.username(),
            member.groupId(),
            member.memberStatus(),
            member.createdDate(),
            member.exitedDate()
        );
    }
}
