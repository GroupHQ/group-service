package org.grouphq.groupservice.group.demo;

import com.github.javafaker.Faker;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grouphq.groupservice.ai.OpenAiGroupGeneratorService;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * A service for generating demo groups.
 * Uses the OpenAI API to generate group postings.
 * Falls back to generating a random group using 'Lorem Ipsum' if the OpenAI API request fails.
 */
@RequiredArgsConstructor
@Slf4j
@Service
public class GroupGeneratorService {

    private final OpenAiGroupGeneratorService openAiGroupGeneratorService;
    private static final Random RANDOM = new Random();

    public Mono<Tuple2<Group, CharacterEntity>> generateGroup(CharacterEntity characterEntity) {
        final int maxCapacity = getRandomGroupCapacity();

        return openAiGroupGeneratorService.generateGroup(characterEntity, maxCapacity)
            .doOnError(e -> log.error("Error generating group posting: {}", e.getMessage()))
            .onErrorReturn(Tuples.of(generateGroup(), characterEntity));
    }

    public Group generateGroup() {
        final int maxCapacity = getRandomGroupCapacity();

        final Faker faker = new Faker();

        return Group.of(faker.lorem().sentence(), faker.lorem().sentence(20),
            maxCapacity, GroupStatus.ACTIVE);
    }

    /**
     * Generates a random group capacity following multiples of 10 from 10 to 100 (inclusive).
     *
     * @return a random group capacity
     */
    public static int getRandomGroupCapacity() {
        final int randomCapacity = RANDOM.nextInt(10) + 1;
        return randomCapacity * 10;
    }
}
