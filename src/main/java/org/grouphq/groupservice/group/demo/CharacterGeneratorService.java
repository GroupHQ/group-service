package org.grouphq.groupservice.group.demo;

import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import net.datafaker.providers.base.Superhero;
import net.datafaker.providers.entertainment.*;
import net.datafaker.providers.videogame.LeagueOfLegends;
import net.datafaker.providers.videogame.Overwatch;
import net.datafaker.providers.videogame.Zelda;
import org.grouphq.groupservice.config.GroupProperties;
import org.springframework.stereotype.Service;

/**
 * Service for randomly generating CharacterEntity objects.
 * Provides support for randomly generating unique Character objects based on Character names.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CharacterGeneratorService {

    private final GroupProperties groupProperties;

    private static final Random RANDOM = new Random();
    private static final CharacterEntity DEFAULT_CHARACTER =
        new CharacterEntity("Protopet", "Ratchet & Clank");

    public CharacterEntity createRandomCharacter() {
        final int min = 1;
        final int max = 15;

        final int randomNum = RANDOM.nextInt(max - min) + min; // range of min (inclusive) to max (exclusive)

        return getCharacter(randomNum);
    }

    public CharacterEntity createRandomAndUniqueCharacter(Stream<String> members) {
        final Set<String> currentNames = members.collect(Collectors.toSet());

        final Optional<CharacterEntity> uniqueCharacter = Stream.generate(this::createRandomCharacter)
            .limit(groupProperties.getCharacterGenerator().getUniqueAttempts())
            .filter(characterEntity -> !currentNames.contains(characterEntity.name()))
            .limit(1)
            .findFirst();

        if (uniqueCharacter.isEmpty()) {
            log.warn("Could not generate a unique character within {} generations for set size {}",
                groupProperties.getCharacterGenerator().getUniqueAttempts(), currentNames.size());
            return DEFAULT_CHARACTER;
        }

        return uniqueCharacter.get();
    }

    private CharacterEntity getCharacter(int randomNum) {
        final Faker faker = new Faker();
        return switch (randomNum) {
            case 1:
                yield new CharacterEntity(faker.backToTheFuture().character(), getUniverse(BackToTheFuture.class));
            case 2:
                yield new CharacterEntity(faker.dragonBall().character(), getUniverse(DragonBall.class));
            case 3:
                yield new CharacterEntity(faker.gameOfThrones().character(), getUniverse(GameOfThrones.class));
            case 4:
                yield new CharacterEntity(faker.harryPotter().character(), getUniverse(HarryPotter.class));
            case 5:
                yield new CharacterEntity(faker.zelda().character(), getUniverse(Zelda.class));
            case 6:
                yield new CharacterEntity(faker.starTrek().character(), getUniverse(StarTrek.class));
            case 7:
                yield new CharacterEntity(faker.howIMetYourMother().character(), getUniverse(HowIMetYourMother.class));
            case 8:
                yield new CharacterEntity(faker.lordOfTheRings().character(), getUniverse(LordOfTheRings.class));
            case 9:
                yield new CharacterEntity(faker.leagueOfLegends().champion(), getUniverse(LeagueOfLegends.class));
            case 10:
                yield new CharacterEntity(faker.overwatch().hero(), getUniverse(Overwatch.class));
            case 11:
                yield new CharacterEntity(faker.rickAndMorty().character(), getUniverse(RickAndMorty.class));
            case 12:
                yield new CharacterEntity(faker.superhero().name(), getUniverse(Superhero.class));
            case 13:
                yield new CharacterEntity(faker.witcher().character(), getUniverse(Witcher.class));
            default:
                yield new CharacterEntity("Captain Qwark", "Ratchet & Clank");
        };
    }

    private String getUniverse(Class<?> universeClass) {
        final String universeClassString = universeClass.getSimpleName();
        final String[] words = universeClassString.split("(?<=[a-z])(?=[A-Z])");

        return String.join(" ", words);
    }
}
