package com.grouphq.groupservice.group.domain.groups;

import java.time.Instant;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A group model.
 *
 * @param id A unique ID belonging to a group
 * @param title Group's title or name
 * @param description Information about the group
 * @param maxGroupSize Maximum number of users that can belong to the group
 * @param currentGroupSize Current number of users that are part of the group
 * @param status Status of group {@link GroupStatus}
 * @param lastActive Last action done in the group by some group member
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
        int currentGroupSize,
        GroupStatus status,
        Instant lastActive,

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
    public static Group of(String title, String description,
                           int maxGroupSize, int currentGroupSize, GroupStatus status) {
        return new Group(null, title, description, maxGroupSize, currentGroupSize, status,
            Instant.now(), null, null, null, null, 0);
    }
}
