package org.grouphq.groupservice.ai;

import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grouphq.groupservice.config.OpenAiApiConfig;
import org.grouphq.groupservice.group.demo.CharacterEntity;
import org.grouphq.groupservice.group.domain.groups.Group;
import org.grouphq.groupservice.group.domain.groups.GroupStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;
import reactor.util.retry.Retry;

/**
 * A service for generating demo groups using the OpenAI API.
 */
@RequiredArgsConstructor
@Slf4j
@Service
public class OpenAiGroupGeneratorService {

    private final OpenAiService openAiService;

    private final OpenAiApiConfig openAiApiConfig;

    public Mono<Tuple2<Group, CharacterEntity>> generateGroup(CharacterEntity characterEntity, int maxGroupSize) {
        if (!openAiApiConfig.isEnabled()) {
            return Mono.error(new IllegalStateException("OpenAI API is not enabled"));
        }

        final var retryConfig = openAiApiConfig.getRetryConfig();

        return Mono.fromCallable(() ->
                openAiService.createChatCompletion(buildGroupRequest(characterEntity))
                    .getChoices().get(0))
            .doOnError(e -> log.error("OpenAI API error generating group posting: {}", e.getMessage()))
            .flatMap(this::throwIfUnfinished)
            .map(choice -> {
                final String content = choice.getMessage().getContent();
                final String title = extractTitle(content);
                final String description = extractDescription(content);
                return Tuples.of(Group.of(title, description, maxGroupSize, GroupStatus.ACTIVE), characterEntity);
            })
            .subscribeOn(Schedulers.boundedElastic())
            .retryWhen(Retry.backoff(retryConfig.getMaxAttempts(), Duration.ofMillis(retryConfig.getInitialDelay()))
                .maxBackoff(Duration.ofMillis(retryConfig.getMaxDelay()))
                .jitter(retryConfig.getJitterFactor())
            );
    }


    public static String extractTitle(String input) {
        return input.replaceAll("(?s)Title: (.*?)Description:.*", "$1").trim();
    }

    public static String extractDescription(String input) {
        return input.replaceAll("(?s).*Description: (.*)", "$1").trim();
    }

    private Mono<ChatCompletionChoice> throwIfUnfinished(ChatCompletionChoice choice) {
        Mono<ChatCompletionChoice> choiceMono;

        if ("stop".equals(choice.getFinishReason())) {
            choiceMono = Mono.just(choice);
        } else {
            final String errorMessage = "OpenAI API did not finish generating group posting"
                + " (finish reason: " + choice.getFinishReason() + ")";
            choiceMono = Mono.error(new ChatCompletionChoiceUnfinishedException(errorMessage));
        }

        return choiceMono;
    }

    private ChatCompletionRequest buildGroupRequest(CharacterEntity characterEntity) {
        return ChatCompletionRequest
            .builder()
            .model("gpt-3.5-turbo")
            .messages(getMessages(characterEntity))
            .maxTokens(500)
            .build();
    }

    private List<ChatMessage> getMessages(CharacterEntity characterEntity) {
        return List.of(
            new ChatMessage(ChatMessageRole.SYSTEM.value(), groupHqDescription()),
            new ChatMessage(ChatMessageRole.SYSTEM.value(), groupHqGroupPublisher(characterEntity)),
            new ChatMessage(ChatMessageRole.SYSTEM.value(), groupHqGroupPrompt())
        );
    }

    private String groupHqDescription() {
        return
            """
            GroupHQ is a web service allowing users to post groups to gather other users to join them in activities \
            happening now.
            """;
    }

    private String groupHqGroupPublisher(CharacterEntity characterEntity) {
        return "You are taking the persona of " + characterEntity.getName() + " from " +  characterEntity.getUniverse();
    }

    private String groupHqGroupPrompt() {
        return
            """
            In a short paragraph, create a group posting for an activity in the context of the your characterEntity's \
            universe. Give the group a title prefixed with "Title:" and a description prefixed with \
            "Description:". Write your group posting using the tone and behavior of your characterEntity. \
            Keep the scope of your activity simple and focused. The activity should be taking place now or as \
            soon as other members join.
            """;
    }
}
