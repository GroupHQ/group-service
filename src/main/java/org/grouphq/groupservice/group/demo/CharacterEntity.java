package org.grouphq.groupservice.group.demo;

import com.github.javafaker.BackToTheFuture;
import com.github.javafaker.DragonBall;
import com.github.javafaker.Faker;
import com.github.javafaker.GameOfThrones;
import com.github.javafaker.HarryPotter;
import com.github.javafaker.HowIMetYourMother;
import com.github.javafaker.LeagueOfLegends;
import com.github.javafaker.LordOfTheRings;
import com.github.javafaker.Overwatch;
import com.github.javafaker.RickAndMorty;
import com.github.javafaker.StarTrek;
import com.github.javafaker.Superhero;
import com.github.javafaker.Witcher;
import com.github.javafaker.Zelda;
import java.util.Random;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Randomly generated character data object using the JavaFaker library.
 */
@Getter
@RequiredArgsConstructor
public class CharacterEntity {

    private final String name;
    private final String universe;
    private static final Random RANDOM = new Random();

    public static CharacterEntity createRandomCharacter() {
        final int min = 1;
        final int max = 15;

        final int randomNum = RANDOM.nextInt(max - min) + min; // range of 1 (inclusive) to 15 (exclusive)

        return getCharacter(randomNum);
    }

    private static CharacterEntity getCharacter(int randomNum) {
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

    private static String getUniverse(Class<?> universeClass) {
        final String universeClassString = universeClass.getSimpleName();
        final String[] words = universeClassString.split("(?<=[a-z])(?=[A-Z])");

        return String.join(" ", words);
    }
}
