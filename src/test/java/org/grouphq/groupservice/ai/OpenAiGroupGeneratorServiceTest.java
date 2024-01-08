package org.grouphq.groupservice.ai;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.grouphq.groupservice.config.OpenAiApiConfig;
import org.grouphq.groupservice.group.demo.CharacterEntity;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

@Tag("UnitTest")
@ExtendWith(MockitoExtension.class)
class OpenAiGroupGeneratorServiceTest {
    @Mock
    private OpenAiService openAiService;

    @Spy
    private OpenAiApiConfig openAiApiConfig;

    @InjectMocks
    private OpenAiGroupGeneratorService openAiGroupGeneratorService;

    private static final CharacterEntity CHARACTER_ENTITY = CharacterEntity.createRandomCharacter();

    private static final int MAX_GROUP_SIZE = 10;


    private String sampleContent() {
        return "Title: " + sampleTitle()
            + "Description: " + sampleDescription();
    }

    private static String sampleTitle() {
        return "Galactic Hero Training - Join Captain Qwark!";
    }

    private String sampleDescription() {
        return "Attention all aspiring heroes! ...";
    }

    @Test
    @DisplayName("Generates a group posting based on given Character")
    void testGenerateGroupPosting() {
        final ChatMessage messageStub = new ChatMessage();
        messageStub.setContent(sampleContent());

        final ChatCompletionChoice choiceStub = new ChatCompletionChoice();
        choiceStub.setMessage(messageStub);
        choiceStub.setFinishReason("stop");

        final List<ChatCompletionChoice> choiceList = List.of(choiceStub);

        final ChatCompletionResult resultStub = new ChatCompletionResult();
        resultStub.setChoices(choiceList);

        given(openAiService.createChatCompletion(any(ChatCompletionRequest.class))).willReturn(resultStub);

        StepVerifier.create(openAiGroupGeneratorService.generateGroup(CHARACTER_ENTITY, MAX_GROUP_SIZE))
            .assertNext(tuple -> {
                Assertions.assertThat(tuple.getT1()).satisfies(group -> {
                    Assertions.assertThat(group).isInstanceOf(Group.class);
                    Assertions.assertThat(group.title()).isEqualTo(sampleTitle());
                    Assertions.assertThat(group.description()).isEqualTo(sampleDescription());
                    Assertions.assertThat(group.maxGroupSize()).isEqualTo(MAX_GROUP_SIZE);
                });
                Assertions.assertThat(tuple.getT2()).satisfies(returnedCharacter -> {
                    Assertions.assertThat(returnedCharacter).isInstanceOf(CharacterEntity.class);
                    Assertions.assertThat(returnedCharacter.getName()).isEqualTo(CHARACTER_ENTITY.getName());
                    Assertions.assertThat(returnedCharacter.getUniverse()).isEqualTo(CHARACTER_ENTITY.getUniverse());
                });
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Extracts title from input string")
    void testExtractTitle() {
        final String input = "Title: Galactic Hero Training - Join Captain Qwark!"
            + "Description: Attention all aspiring heroes! ...";
        final String expectedTitle = "Galactic Hero Training - Join Captain Qwark!";

        final String actualTitle = OpenAiGroupGeneratorService.extractTitle(input);

        assertThat(actualTitle).isEqualTo(expectedTitle);
    }

    @Test
    @DisplayName("Extracts description from input string")
    void testExtractDescription() {
        final String input = "Title: Galactic Hero Training - Join Captain Qwark!"
            + "Description: Attention all aspiring heroes! ...";
        final String expectedDescription = "Attention all aspiring heroes! ...";

        final String actualDescription = OpenAiGroupGeneratorService.extractDescription(input);

        assertThat(actualDescription).isEqualTo(expectedDescription);
    }
}
