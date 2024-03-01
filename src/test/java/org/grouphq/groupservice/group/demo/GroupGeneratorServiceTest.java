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
import java.util.stream.Stream;
import org.grouphq.groupservice.config.OpenAiApiConfig;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

@SpringBootTest
@Testcontainers
@Tag("IntegrationTest")
class GroupGeneratorServiceTest {

    @MockBean
    private OpenAiService openAiService;

    @SpyBean
    private OpenAiApiConfig openAiApiConfig;

    @Autowired
    private GroupGeneratorService groupGeneratorService;

    @Autowired
    private CharacterGeneratorService characterGeneratorService;

    private CharacterEntity characterEntity;

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:14.4"));

    @DynamicPropertySource
    private static void postgresqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", GroupGeneratorServiceTest::r2dbcUrl);
        registry.add("spring.r2dbc.username", POSTGRESQL_CONTAINER::getUsername);
        registry.add("spring.r2dbc.password", POSTGRESQL_CONTAINER::getPassword);
        registry.add("spring.flyway.url", POSTGRESQL_CONTAINER::getJdbcUrl);
    }

    private static String r2dbcUrl() {
        return String.format("r2dbc:postgresql://%s:%s/%s", POSTGRESQL_CONTAINER.getHost(),
            POSTGRESQL_CONTAINER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
            POSTGRESQL_CONTAINER.getDatabaseName());
    }
    

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

    @BeforeEach
    void createCharacter() {
        characterEntity = characterGeneratorService.createRandomCharacter();
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

        given(openAiApiConfig.isEnabled()).willReturn(true);
        given(openAiService.createChatCompletion(any(ChatCompletionRequest.class))).willReturn(resultStub);

        StepVerifier.create(groupGeneratorService.generateGroup(characterEntity))
            .assertNext(tuple -> {
                assertThat(tuple.getT1()).satisfies(group -> {
                    assertThat(group).isInstanceOf(Group.class);
                    assertThat(group.title()).isEqualTo(sampleTitle());
                    assertThat(group.description()).isEqualTo(sampleDescription());
                });
                assertThat(tuple.getT2()).satisfies(returnedCharacter -> {
                    assertThat(returnedCharacter).isInstanceOf(CharacterEntity.class);
                    assertThat(returnedCharacter.name()).isEqualTo(characterEntity.name());
                    assertThat(returnedCharacter.universe()).isEqualTo(characterEntity.universe());
                });
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Generates a group using fallback method if OpenAI API call fails")
    void testGenerateGroupPostingFallback() {
        given(openAiApiConfig.isEnabled()).willReturn(true);
        given(openAiService.createChatCompletion(any(ChatCompletionRequest.class)))
            .willThrow(OpenAiHttpException.class);

        final int maxDelay = openAiApiConfig.getRetryConfig().getMaxDelay();
        final int maxAttempts = openAiApiConfig.getRetryConfig().getMaxAttempts();

        StepVerifier.withVirtualTime(() -> groupGeneratorService.generateGroup(characterEntity))
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
                    assertThat(returnedCharacter.name()).isEqualTo(characterEntity.name());
                    assertThat(returnedCharacter.universe()).isEqualTo(characterEntity.universe());
                });
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("Returns a group capacity greater than 0 and a multiple of 10")
    void returnGroupCapacityGreaterThanZeroAndMultipleOfTen() {
        Stream.iterate(0, i -> i < 100, i -> i + 1)
            .forEach(i -> {
                final int capacity = GroupGeneratorService.getRandomGroupCapacity();
                assertThat(capacity).isGreaterThan(0);
                assertThat(capacity % 10).isEqualTo(0);
            });
    }
}
