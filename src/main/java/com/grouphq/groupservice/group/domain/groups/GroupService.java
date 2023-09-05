package com.grouphq.groupservice.group.domain.groups;

import com.github.javafaker.Faker;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A service performing the main business logic for the Group Service application.
 */
@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final int expiryTime;

    public GroupService(GroupRepository groupRepository,
                        @Value("${group.expiry-checker.time}") int expiryTime) {
        this.groupRepository = groupRepository;
        this.expiryTime = expiryTime;
    }

    public Flux<Group> getGroups() {
        return groupRepository.findGroupsByStatus(GroupStatus.ACTIVE);
    }

    public Mono<Integer> expireGroups() {
        final Instant expiryDate = Instant.now().minus(expiryTime, ChronoUnit.SECONDS);
        return groupRepository.expireGroupsPastExpiryDate(expiryDate, GroupStatus.AUTO_DISBANDED);
    }

    /**
     * Generates a random group that a user may have created.
     */
    public Group generateGroup() {
        final Faker faker = new Faker();

        // Generate capacities and ensure maxCapacity has the higher number
        int currentCapacity = faker.number().numberBetween(1, 249);
        int maxCapacity = faker.number().numberBetween(2, 250);
        final int temp = maxCapacity;
        maxCapacity = Math.max(currentCapacity, maxCapacity);
        currentCapacity = Math.min(currentCapacity, temp);

        return Group.of(faker.lorem().sentence(), faker.lorem().sentence(20),
            maxCapacity, currentCapacity, GroupStatus.ACTIVE);
    }
}
