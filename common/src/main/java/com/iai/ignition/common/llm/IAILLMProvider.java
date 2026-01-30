package com.iai.ignition.common.llm;

import java.util.List;

/**
 * Interface for LLM providers.
 * Allows for different LLM implementations (Claude, etc.) to be swapped.
 */
public interface IAILLMProvider {

    /**
     * Send a message to the LLM and get a response.
     *
     * @param request The LLM request containing messages, tools, and configuration
     * @return The LLM response with content, tool calls, and token usage
     * @throws LLMException if the request fails
     */
    LLMResponse sendMessage(LLMRequest request) throws LLMException;

    /**
     * Get the provider name (e.g., "Claude", "OpenAI").
     *
     * @return Provider name
     */
    String getProviderName();

    /**
     * Check if the provider is properly configured and ready to use.
     *
     * @return true if configured and ready
     */
    boolean isConfigured();
}
