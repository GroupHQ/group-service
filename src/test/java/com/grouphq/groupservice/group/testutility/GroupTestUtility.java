package com.grouphq.groupservice.group.testutility;

import com.github.javafaker.Faker;
import com.grouphq.groupservice.group.domain.groups.Group;
import com.grouphq.groupservice.group.domain.groups.GroupStatus;
import java.time.Instant;

/**
 * Utility class for common functionality needed by multiple tests.
 */
public final class GroupTestUtility {

    private GroupTestUtility() {}

    /**
     * Generates a group that would be found in the database.
     *
     * @return A group object with all details
     */
    public static Group generateFullGroupDetails() {
        final Faker faker = new Faker();

        // Generate capacities and ensure maxCapacity has the higher number
        int currentCapacity = faker.number().numberBetween(1, 249);
        int maxCapacity = faker.number().numberBetween(2, 250);
        final int temp = maxCapacity;
        maxCapacity = Math.max(currentCapacity, maxCapacity);
        currentCapacity = Math.min(currentCapacity, temp);

        return new Group(
            faker.number().randomNumber(10, true),
            faker.lorem().sentence(),
            faker.lorem().sentence(20),
            maxCapacity,
            currentCapacity,
            GroupStatus.ACTIVE,
            Instant.now(),
            Instant.now(),
            Instant.now(),
            "system",
            "system",
            0
        );
    }
}
