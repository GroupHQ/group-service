package com.grouphq.groupservice.group.testutility;

import com.github.javafaker.Faker;
import com.grouphq.groupservice.group.domain.groups.Group;
import com.grouphq.groupservice.group.domain.groups.GroupStatus;
import com.grouphq.groupservice.group.domain.members.Member;
import com.grouphq.groupservice.group.domain.members.MemberStatus;
import com.grouphq.groupservice.group.domain.outbox.OutboxEvent;
import com.grouphq.groupservice.group.domain.outbox.enums.AggregateType;
import com.grouphq.groupservice.group.domain.outbox.enums.EventStatus;
import com.grouphq.groupservice.group.domain.outbox.enums.EventType;
import com.grouphq.groupservice.group.event.daos.GroupCreateRequestEvent;
import com.grouphq.groupservice.group.event.daos.GroupJoinRequestEvent;
import com.grouphq.groupservice.group.event.daos.GroupLeaveRequestEvent;
import com.grouphq.groupservice.group.event.daos.GroupStatusRequestEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Utility class for common functionality needed by multiple tests.
 */
public final class GroupTestUtility {

    static final String OWNER = "system";

    private GroupTestUtility() {}

    /**
     * Generates a group that would be found in the database.
     * Note that IDs are intentionally 12 digits so that they'll always be considered a
     * {@code long} type. Otherwise, it will introduce flakiness to the JsonTests as they
     * dynamically assign an {@code int} or {@code long} type based on if the number is at
     * or below {@code Integer.MAX_VALUE}.
     *
     * @param status the status of the group.
     *
     * @return A group object with all details.
     */
    public static Group generateFullGroupDetails(GroupStatus status) {
        final Faker faker = new Faker();

        // Generate capacities and ensure space for at least 50 members to join
        final int currentCapacity = faker.number().numberBetween(1, 50);
        final int maxCapacity = faker.number().numberBetween(100, 150);

        return new Group(
            faker.number().randomNumber(12, true),
            faker.lorem().sentence(),
            faker.lorem().sentence(20),
            maxCapacity,
            currentCapacity,
            status,
            Instant.now(),
            Instant.now(),
            Instant.now(),
            OWNER,
            OWNER,
            0
        );
    }

    /**
     * Overloaded method for {@link #generateFullGroupDetails(GroupStatus status)}.
     *
     * @param groupId the group's id.
     * @param status the status of the group.
     *
     * @return a Group object with all details.
     */
    public static Group generateFullGroupDetails(Long groupId, GroupStatus status) {
        final Faker faker = new Faker();

        // Generate capacities and ensure space for at least 50 members to join
        final int currentCapacity = faker.number().numberBetween(1, 50);
        final int maxCapacity = faker.number().numberBetween(100, 150);

        return new Group(
            groupId,
            faker.lorem().sentence(),
            faker.lorem().sentence(20),
            maxCapacity,
            currentCapacity,
            status,
            Instant.now(),
            Instant.now(),
            Instant.now(),
            OWNER,
            OWNER,
            0
        );
    }

    /**
     * Overloaded method for {@link #generateFullGroupDetails(GroupStatus status)}.
     *
     * @param maxGroupSize maximum number of users that can belong to the group.
     * @param currentGroupSize current number of users that are part of the group.
     * @param status the status of the group.
     *
     * @return a group object with all details.
     */
    public static Group generateFullGroupDetails(
        int maxGroupSize,
        int currentGroupSize,
        GroupStatus status) {
        final Faker faker = new Faker();

        return new Group(
            faker.number().randomNumber(12, true),
            faker.lorem().sentence(),
            faker.lorem().sentence(20),
            maxGroupSize,
            currentGroupSize,
            status,
            Instant.now(),
            Instant.now(),
            Instant.now(),
            OWNER,
            OWNER,
            0
        );
    }

    /**
     * Generates a member that would be found in the database. Note that IDs are
     * intentionally 12 digits so that they'll always be considered a {@code long} type.
     *
     * @see #generateFullGroupDetails(GroupStatus status) for more info.
     *
     * @return A group object with all details.
     */
    public static Member generateFullMemberDetails() {
        final Faker faker = new Faker();

        return new Member(
            faker.number().randomNumber(12, true),
            faker.name().firstName(),
            faker.number().randomNumber(12, true),
            MemberStatus.ACTIVE,
            Instant.now(),
            null,
            Instant.now(),
            Instant.now(),
            OWNER,
            OWNER,
            0
        );
    }

    /**
     * Generates a member that would be found in the database. Note that IDs are
     * intentionally 12 digits so that they'll always be considered a {@code long} type.
     *
     * @see #generateFullGroupDetails(GroupStatus status) for more info.
     *
     * @param username the username of the member.
     * @param groupId the group ID the member belongs to.
     *
     * @return A group object with all details.
     */
    public static Member generateFullMemberDetails(String username, Long groupId) {
        final Faker faker = new Faker();

        return new Member(
            faker.number().randomNumber(12, true),
            username,
            groupId,
            MemberStatus.ACTIVE,
            Instant.now(),
            null,
            Instant.now(),
            Instant.now(),
            OWNER,
            OWNER,
            0
        );
    }

    /**
     * Overloaded method for {@link #generateFullMemberDetails()}.
     *
     * @see #generateFullGroupDetails(GroupStatus status) for more info.
     *
     * @return a GroupJoinRequestEvent object with all details.
     */
    public static GroupJoinRequestEvent generateGroupJoinRequestEvent() {
        final Faker faker = new Faker();

        return new GroupJoinRequestEvent(
            UUID.randomUUID(),
            faker.number().randomNumber(12, true),
            faker.name().firstName(),
            faker.number().digits(36),
            Instant.now()
        );
    }

    /**
     * Overloaded method for {@link #generateGroupJoinRequestEvent()}.
     *
     * @param groupId the group ID the member is requesting to join.
     *
     * @return a GroupJoinRequestEvent object with all details.
     */
    public static GroupJoinRequestEvent generateGroupJoinRequestEvent(Long groupId) {
        final Faker faker = new Faker();

        return new GroupJoinRequestEvent(
            UUID.randomUUID(),
            groupId,
            faker.name().firstName(),
            faker.number().digits(36),
            Instant.now()
        );
    }

    /**
     * Generates a group leave request event.
     * Note that IDs are intentionally 12 digits so that
     * they'll always be considered a {@code long} type.
     *
     * @see #generateFullGroupDetails(GroupStatus status) for more info.
     *
     * @return a GroupLeaveRequestEvent object with all details.
     */
    public static GroupLeaveRequestEvent generateGroupLeaveRequestEvent() {
        final Faker faker = new Faker();

        return new GroupLeaveRequestEvent(
            UUID.randomUUID(),
            faker.number().randomNumber(12, true),
            faker.number().randomNumber(12, true),
            faker.number().digits(36),
            Instant.now()
        );
    }

    /**
     * Overloaded method for {@link #generateGroupLeaveRequestEvent()}.
     *
     * @param groupId the group ID the member is requesting to leave.
     * @param memberId the member ID that is requesting to leave the group.
     *
     * @return a GroupLeaveRequestEvent object with all details.
     */
    public static GroupLeaveRequestEvent generateGroupLeaveRequestEvent(
        Long groupId, Long memberId) {

        final Faker faker = new Faker();

        return new GroupLeaveRequestEvent(
            UUID.randomUUID(),
            groupId,
            memberId,
            faker.number().digits(36),
            Instant.now()
        );
    }

    /**
     * Generates a group create request event.
     *
     * @return a GroupCreateRequestEvent object with all details.
     */
    public static GroupCreateRequestEvent generateGroupCreateRequestEvent() {
        final Faker faker = new Faker();

        // Generate capacities and ensure space for at least 50 members to join
        final int currentCapacity = faker.number().numberBetween(1, 50);
        final int maxCapacity = faker.number().numberBetween(100, 150);

        return new GroupCreateRequestEvent(
            UUID.randomUUID(),
            faker.lorem().sentence(),
            faker.lorem().sentence(20),
            maxCapacity,
            currentCapacity,
            OWNER,
            faker.number().digits(36),
            Instant.now()
        );
    }

    /**
     * Overloaded method for {@link #generateGroupCreateRequestEvent()}.
     *
     * @param maxCapacity the maximum number of members that can join the group.
     * @param currentCapacity the current number of members that are part of the group.
     *
     * @return a GroupCreateRequestEvent object with all details.
     */
    public static GroupCreateRequestEvent generateGroupCreateRequestEvent(
        int maxCapacity, int currentCapacity) {

        final Faker faker = new Faker();

        return new GroupCreateRequestEvent(
            UUID.randomUUID(),
            faker.lorem().sentence(),
            faker.lorem().sentence(20),
            maxCapacity,
            currentCapacity,
            OWNER,
            faker.number().digits(36),
            Instant.now()
        );
    }

    /**
     * Generates a group status request event.
     *
     * @param groupId the group ID to update status for.
     * @param status the status of the group.
     *
     * @return a GroupStatusRequestEvent object with all details.
     */
    public static GroupStatusRequestEvent generateGroupStatusRequestEvent(
        Long groupId, GroupStatus status) {
        final Faker faker = new Faker();

        return new GroupStatusRequestEvent(
            UUID.randomUUID(),
            groupId,
            status,
            faker.number().digits(36),
            Instant.now()
        );
    }

    /**
     * Generates an outbox event.
     *
     * @return an OutboxEvent object with all details.
     */
    public static OutboxEvent generateOutboxEvent() {
        final Faker faker = new Faker();

        return new OutboxEvent(
            UUID.randomUUID(),
            faker.number().randomNumber(12, true),
            faker.number().digits(36),
            AggregateType.GROUP,
            EventType.GROUP_CREATED,
            "{\"status\": \"ACTIVE\"}",
            EventStatus.SUCCESSFUL,
            Instant.now()
        );
    }
}
