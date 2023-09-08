package com.grouphq.groupservice.group.testutility;

import com.github.javafaker.Faker;
import com.grouphq.groupservice.group.domain.groups.Group;
import com.grouphq.groupservice.group.domain.groups.GroupStatus;
import com.grouphq.groupservice.group.domain.members.Member;
import com.grouphq.groupservice.group.domain.members.MemberStatus;
import java.time.Instant;

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
     * @return A group object with all details
     */
    public static Group generateFullGroupDetails() {
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
            GroupStatus.ACTIVE,
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
     * intentionally 12 digits so that they'll always be considered a {@code long} type
     *
     * @see #generateFullGroupDetails() for more info.
     *
     * @return A group object with all details
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
}
