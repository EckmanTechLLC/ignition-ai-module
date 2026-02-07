package com.iai.ignition.gateway.util;

import com.iai.ignition.common.model.Message;

import java.util.List;

/**
 * Utility for estimating token counts before sending to LLM API.
 * Uses character-based estimation since exact tokenization requires the model's tokenizer.
 */
public class TokenCounter {

    // Approximate tokens per character (based on Claude tokenizer behavior)
    private static final double CHARS_PER_TOKEN = 3.5;

    /**
     * Estimate token count for a single text string.
     *
     * @param text The text to count
     * @return Estimated token count
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    /**
     * Estimate token count for a message (content only).
     *
     * @param message The message to count
     * @return Estimated token count
     */
    public static int estimateTokens(Message message) {
        if (message == null || message.getContent() == null) {
            return 0;
        }
        return estimateTokens(message.getContent());
    }

    /**
     * Estimate total token count for a list of messages.
     *
     * @param messages The messages to count
     * @return Estimated total token count
     */
    public static int estimateTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (Message msg : messages) {
            total += estimateTokens(msg);
        }
        return total;
    }

    /**
     * Estimate token count for system prompt.
     *
     * @param systemPrompt The system prompt text
     * @return Estimated token count
     */
    public static int estimateSystemPromptTokens(String systemPrompt) {
        return estimateTokens(systemPrompt);
    }

    /**
     * Check if token count is approaching the context limit.
     *
     * @param tokenCount Current token count
     * @param limit Context limit (e.g., 200000)
     * @param threshold Warning threshold percentage (e.g., 0.9 for 90%)
     * @return true if approaching limit
     */
    public static boolean isApproachingLimit(int tokenCount, int limit, double threshold) {
        return tokenCount >= (limit * threshold);
    }
}
