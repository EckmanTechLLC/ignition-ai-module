package com.iai.ignition.common.llm;

import com.iai.ignition.common.model.ToolCall;

import java.io.Serializable;
import java.util.List;

/**
 * Represents a response from an LLM provider.
 */
public class LLMResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String content;
    private List<ToolCall> toolCalls;
    private int inputTokens;
    private int outputTokens;
    private String stopReason;

    /**
     * No-arg constructor for serialization.
     */
    public LLMResponse() {
    }

    /**
     * Full constructor.
     */
    public LLMResponse(String content, List<ToolCall> toolCalls, int inputTokens, int outputTokens, String stopReason) {
        this.content = content;
        this.toolCalls = toolCalls;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.stopReason = stopReason;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(int inputTokens) {
        this.inputTokens = inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(int outputTokens) {
        this.outputTokens = outputTokens;
    }

    public String getStopReason() {
        return stopReason;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }
}
