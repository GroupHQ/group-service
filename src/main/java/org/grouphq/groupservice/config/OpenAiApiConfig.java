package org.grouphq.groupservice.config;

import com.theokanning.openai.service.OpenAiService;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the OpenAiService bean and related properties.
 */
@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "openai")
public class OpenAiApiConfig {

    @NotNull(message = "OpenAI API key must be provided")
    private String apiKey;

    @NotNull(message = "OpenAI model Id must be provided")
    private String modelId;

    private int maxTokens;

    private RetryConfig retryConfig = new RetryConfig();

    @Bean
    public OpenAiService openAiService() {
        return new OpenAiService(apiKey);
    }

    /**
     * Configuration properties for the retrying OpenAI API requests.
     */
    @Setter
    @Getter
    public static class RetryConfig {
        private int maxAttempts;
        private int initialDelay;
        private int maxDelay;
        private int jitterFactor;
    }
}
