package org.grouphq.groupservice.group.domain.groups;

import java.time.Instant;
import java.util.List;
import org.grouphq.groupservice.group.web.objects.egress.PublicMember;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A group model.
 *
 * @param id A unique ID belonging to a group
 * @param title Group's title or name
 * @param description Information about the group
 * @param maxGroupSize Maximum number of users that can belong to the group
 * @param status Status of group {@link GroupStatus}
 * @param lastMemberActivity Time last action done in the
 *                           group by some group member (e.g., joining or leaving)
 * @param createdDate Time group was created
 * @param lastModifiedDate Time group was last modified / updated
 * @param createdBy Creator of group (e.g. system or user)
 * @param lastModifiedBy Who last modified the group
 * @param version Unique number on group state (used by Spring Data for optimistic locking)
 */
@Table("groups")
public record Group(

        @Id
        Long id,

        String title,
        String description,
        int maxGroupSize,
        GroupStatus status,
        Instant lastMemberActivity,
        @CreatedDate
        Instant createdDate,

        @LastModifiedDate
        Instant lastModifiedDate,

        @CreatedBy
        String createdBy,

        @LastModifiedBy
        String lastModifiedBy,

        @Version
        int version,

        @Transient
        List<PublicMember> members
) {
    // We can't use the record's default constructor because Spring Data JDBC
    // will complain about the @Transient members property.
    // As of now, we'll use the @PersistenceCreator annotation to tell Spring Data JDBC to use
    // this factory method instead.
    // The Spring Data team is working on an enhancement to ignore properties
    // marked with @Transient in the default constructor.
    // A pull request for this change is currently under
    // review as of 12/21/2023: https://github.com/spring-projects/spring-data-commons/pull/2985
    @PersistenceCreator
    public static Group of(Long id, String title, String description,
                           int maxGroupSize, GroupStatus status, Instant lastMemberActivity,
                           Instant createdDate, Instant lastModifiedDate,
                           String createdBy, String lastModifiedBy,
                           int version) {
        return new Group(id, title, description, maxGroupSize, status, lastMemberActivity,
            createdDate, lastModifiedDate, createdBy, lastModifiedBy,
            version, null);
    }


    public static Group of(String title, String description,
                           int maxGroupSize, GroupStatus status) {
        return new Group(null, title, description, maxGroupSize, status, null,
            null, null, null, null, 0, null);
    }

    public Group withDescription(String description) {
        return new Group(
            this.id(),
            this.title(),
            description,
            this.maxGroupSize(),
            this.status(),
            this.lastMemberActivity(),
            this.createdDate(),
            this.lastModifiedDate(),
            this.createdBy(),
            this.lastModifiedBy(),
            this.version(),
            this.members()
        );
    }

    public Group withStatus(GroupStatus status) {
        return new Group(
            this.id(),
            this.title(),
            this.description(),
            this.maxGroupSize(),
            status,
            this.lastMemberActivity(),
            this.createdDate(),
            this.lastModifiedDate(),
            this.createdBy(),
            this.lastModifiedBy(),
            this.version(),
            this.members()
        );
    }

    public Group withLastMemberActivity(Instant lastMemberActivity) {
        return new Group(
            this.id(),
            this.title(),
            this.description(),
            this.maxGroupSize(),
            this.status(),
            lastMemberActivity,
            this.createdDate(),
            this.lastModifiedDate(),
            this.createdBy(),
            this.lastModifiedBy(),
            this.version(),
            this.members()
        );
    }

    public Group withMembers(List<PublicMember> members) {
        return new Group(
            this.id(),
            this.title(),
            this.description(),
            this.maxGroupSize(),
            this.status(),
            this.lastMemberActivity(),
            this.createdDate(),
            this.lastModifiedDate(),
            this.createdBy(),
            this.lastModifiedBy(),
            this.version(),
            members
        );
    }
}
