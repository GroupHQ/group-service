package org.grouphq.groupservice.group.demo;

import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datafaker.Faker;
import net.datafaker.providers.base.Community;
import net.datafaker.providers.base.Superhero;
import net.datafaker.providers.entertainment.BackToTheFuture;
import net.datafaker.providers.entertainment.BigBangTheory;
import net.datafaker.providers.entertainment.BreakingBad;
import net.datafaker.providers.entertainment.BrooklynNineNine;
import net.datafaker.providers.entertainment.DoctorWho;
import net.datafaker.providers.entertainment.DragonBall;
import net.datafaker.providers.entertainment.FamilyGuy;
import net.datafaker.providers.entertainment.FreshPrinceOfBelAir;
import net.datafaker.providers.entertainment.Futurama;
import net.datafaker.providers.entertainment.GameOfThrones;
import net.datafaker.providers.entertainment.Ghostbusters;
import net.datafaker.providers.entertainment.HarryPotter;
import net.datafaker.providers.entertainment.HeyArnold;
import net.datafaker.providers.entertainment.Hobbit;
import net.datafaker.providers.entertainment.HowIMetYourMother;
import net.datafaker.providers.entertainment.HowToTrainYourDragon;
import net.datafaker.providers.entertainment.LordOfTheRings;
import net.datafaker.providers.entertainment.Naruto;
import net.datafaker.providers.entertainment.OnePiece;
import net.datafaker.providers.entertainment.ResidentEvil;
import net.datafaker.providers.entertainment.RickAndMorty;
import net.datafaker.providers.entertainment.Seinfeld;
import net.datafaker.providers.entertainment.Simpsons;
import net.datafaker.providers.entertainment.SouthPark;
import net.datafaker.providers.entertainment.Spongebob;
import net.datafaker.providers.entertainment.StarTrek;
import net.datafaker.providers.entertainment.StarWars;
import net.datafaker.providers.entertainment.StrangerThings;
import net.datafaker.providers.entertainment.Supernatural;
import net.datafaker.providers.entertainment.Witcher;
import net.datafaker.providers.videogame.FinalFantasyXIV;
import net.datafaker.providers.videogame.LeagueOfLegends;
import net.datafaker.providers.videogame.MassEffect;
import net.datafaker.providers.videogame.Overwatch;
import net.datafaker.providers.videogame.RedDeadRedemption2;
import net.datafaker.providers.videogame.SonicTheHedgehog;
import net.datafaker.providers.videogame.StreetFighter;
import net.datafaker.providers.videogame.SuperMario;
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
        final int max = 43;

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
                yield create(faker.backToTheFuture().character(), BackToTheFuture.class);
            case 2:
                yield create(faker.dragonBall().character(), DragonBall.class);
            case 3:
                yield create(faker.gameOfThrones().character(), GameOfThrones.class);
            case 4:
                yield create(faker.harryPotter().character(), HarryPotter.class);
            case 5:
                yield create(faker.zelda().character(), Zelda.class);
            case 6:
                yield create(faker.starTrek().character(), StarTrek.class);
            case 7:
                yield create(faker.howIMetYourMother().character(), HowIMetYourMother.class);
            case 8:
                yield create(faker.lordOfTheRings().character(), LordOfTheRings.class);
            case 9:
                yield create(faker.leagueOfLegends().champion(), LeagueOfLegends.class);
            case 10:
                yield create(faker.overwatch().hero(), Overwatch.class);
            case 11:
                yield create(faker.rickAndMorty().character(), RickAndMorty.class);
            case 12:
                yield create(faker.superhero().name(), Superhero.class);
            case 13:
                yield create(faker.witcher().character(), Witcher.class);
            case 14:
                yield create(faker.starWars().character(), StarWars.class);
            case 15:
                yield create(faker.simpsons().character(), Simpsons.class);
            case 16:
                yield create(faker.southPark().characters(), SouthPark.class);
            case 17:
                yield create(faker.bigBangTheory().character(), BigBangTheory.class);
            case 18:
                yield create(faker.futurama().character(), Futurama.class);
            case 19:
                yield create(faker.breakingBad().character(), BreakingBad.class);
            case 20:
                yield create(faker.brooklynNineNine().characters(), BrooklynNineNine.class);
            case 21:
                yield create(faker.community().character(), Community.class);
            case 22:
                yield create(faker.doctorWho().character(), DoctorWho.class);
            case 23:
                yield create(faker.familyGuy().character(), FamilyGuy.class);
            case 24:
                yield create(faker.finalFantasyXIV().character(), FinalFantasyXIV.class);
            case 25:
                yield create(faker.freshPrinceOfBelAir().characters(), FreshPrinceOfBelAir.class);
            case 26:
                yield create(faker.ghostbusters().character(), Ghostbusters.class);
            case 27:
                yield create(faker.heyArnold().characters(), HeyArnold.class);
            case 28:
                yield create(faker.hobbit().character(), Hobbit.class);
            case 29:
                yield create(faker.howToTrainYourDragon().characters(), HowToTrainYourDragon.class);
            case 30:
                yield create(faker.massEffect().character(), MassEffect.class);
            case 31:
                yield create(faker.naruto().character(), Naruto.class);
            case 32:
                yield create(faker.onePiece().character(), OnePiece.class);
            case 33:
                yield create(faker.redDeadRedemption2().majorCharacter(), RedDeadRedemption2.class);
            case 34:
                yield create(faker.residentEvil().character(), ResidentEvil.class);
            case 35:
                yield create(faker.seinfeld().character(), Seinfeld.class);
            case 36:
                yield create(faker.sonicTheHedgehog().character(), SonicTheHedgehog.class);
            case 37:
                yield create(faker.spongebob().characters(), Spongebob.class);
            case 38:
                yield create(faker.strangerThings().character(), StrangerThings.class);
            case 39:
                yield create(faker.streetFighter().characters(), StreetFighter.class);
            case 40:
                yield create(faker.superMario().characters(), SuperMario.class);
            case 41:
                yield create(faker.supernatural().character(), Supernatural.class);
            default:
                yield new CharacterEntity("Captain Qwark", "Ratchet & Clank");
        };
    }

    private <T> CharacterEntity create(String name, Class<T> universe) {
        return new CharacterEntity(name, getUniverse(universe));
    }

    private String getUniverse(Class<?> universeClass) {
        final String universeClassString = universeClass.getSimpleName();
        final String[] words = universeClassString.split("(?<=[a-z])(?=[A-Z])");

        return String.join(" ", words);
    }
}
