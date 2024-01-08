package org.grouphq.groupservice.group.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import java.time.Duration;
import java.util.List;
import org.grouphq.groupservice.config.OpenAiApiConfig;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import reactor.test.StepVerifier;

@SpringBootTest
@Tag("IntegrationTest")
class GroupGeneratorServiceTest {

    @MockBean
    private OpenAiService openAiService;

    @SpyBean
    private OpenAiApiConfig openAiApiConfig;

    @Autowired
    private GroupGeneratorService groupGeneratorService;

    private static final CharacterEntity CHARACTER_ENTITY = CharacterEntity.createRandomCharacter();
    

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
    @DisplayName("Generates a group posting based on given Character using OpenAI API")
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

        StepVerifier.create(groupGeneratorService.generateGroup(CHARACTER_ENTITY))
            .assertNext(tuple -> {
                assertThat(tuple.getT1()).satisfies(group -> {
                    assertThat(group).isInstanceOf(Group.class);
                    assertThat(group.title()).isEqualTo(sampleTitle());
                    assertThat(group.description()).isEqualTo(sampleDescription());
                });
                assertThat(tuple.getT2()).satisfies(returnedCharacter -> {
                    assertThat(returnedCharacter).isInstanceOf(CharacterEntity.class);
                    assertThat(returnedCharacter.getName()).isEqualTo(CHARACTER_ENTITY.getName());
                    assertThat(returnedCharacter.getUniverse()).isEqualTo(CHARACTER_ENTITY.getUniverse());
                });
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Generates a group using fallback method if OpenAI API call fails")
    void testGenerateGroupPostingFallback() {
        given(openAiService.createChatCompletion(any(ChatCompletionRequest.class)))
            .willThrow(OpenAiHttpException.class);

        final int maxDelay = openAiApiConfig.getRetryConfig().getMaxDelay();
        final int maxAttempts = openAiApiConfig.getRetryConfig().getMaxAttempts();

        StepVerifier.withVirtualTime(() -> groupGeneratorService.generateGroup(CHARACTER_ENTITY))
            .thenAwait(Duration.ofMillis((long) maxDelay * maxAttempts))
            .assertNext(tuple -> {
                assertThat(tuple.getT1()).satisfies(group -> {
                    assertThat(group).isInstanceOf(Group.class);
                    assertThat(group.title()).isNotBlank();
                    assertThat(group.description()).isNotBlank();
                    assertThat(group.title()).isNotEqualTo(sampleTitle());
                    assertThat(group.description()).isNotEqualTo(sampleDescription());
                });
                assertThat(tuple.getT2()).satisfies(returnedCharacter -> {
                    assertThat(returnedCharacter).isInstanceOf(CharacterEntity.class);
                    assertThat(returnedCharacter.getName()).isEqualTo(CHARACTER_ENTITY.getName());
                    assertThat(returnedCharacter.getUniverse()).isEqualTo(CHARACTER_ENTITY.getUniverse());
                });
            })
            .verifyComplete();
    }
}
