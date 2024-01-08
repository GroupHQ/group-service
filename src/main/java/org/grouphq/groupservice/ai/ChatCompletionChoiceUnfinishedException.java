package org.grouphq.groupservice.ai;

/**
 * Exception thrown when the OpenAI API returns a response with an unfinished completion choice.
 */
public class ChatCompletionChoiceUnfinishedException extends RuntimeException {
    public ChatCompletionChoiceUnfinishedException(String message) {
        super(message);
    }
}
