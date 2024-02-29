package org.grouphq.groupservice.group.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.grouphq.groupservice.config.GroupProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("UnitTest")
@ExtendWith(MockitoExtension.class)
class CharacterGeneratorServiceTest {

    @Spy
    private GroupProperties groupProperties;

    @InjectMocks
    private CharacterGeneratorService characterGeneratorService;

    @Test
    @DisplayName("Returns a random character with some name and universe")
    void returnRandomCharacter() {
        final CharacterEntity characterEntity = characterGeneratorService.createRandomCharacter();

        assert characterEntity != null;

        assertThat(characterEntity.name()).isNotBlank();
        assertThat(characterEntity.universe()).isNotBlank();
    }

    @Test
    @DisplayName("Returns a random and unique character based on passed set of names")
    void returnRandomAndUniqueCharacter() {
        final GroupProperties.CharacterGenerator characterGeneratorMock =
            new GroupProperties.CharacterGenerator();
        characterGeneratorMock.setUniqueAttempts(1000);

        given(groupProperties.getCharacterGenerator())
            .willReturn(characterGeneratorMock);

        final Set<String> characterSet = new HashSet<>();

        Stream.iterate(0, i -> i < 100, i -> i + 1)
            .forEach(i -> {
                final Stream<String> characterNames = characterSet.stream();
                final CharacterEntity characterEntity =
                    characterGeneratorService.createRandomAndUniqueCharacter(characterNames);

                assertThat(characterSet).doesNotContain(characterEntity.name());

                characterSet.add(characterEntity.name());
            });
    }
}
