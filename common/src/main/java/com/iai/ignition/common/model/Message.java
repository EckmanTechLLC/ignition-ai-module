package com.iai.ignition.common.model;

import java.io.Serializable;
import java.util.List;

/**
 * Represents a message in a conversation.
 * Corresponds to the iai_messages database table.
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String conversationId;
    private String role;
    private String content;
    private List<ToolCall> toolCalls;
    private List<ToolResult> toolResults;
    private Integer inputTokens;
    private Integer outputTokens;
    private long timestamp;

    /**
     * No-arg constructor for serialization.
     */
    public Message() {
    }

    /**
     * Full constructor for creating new messages.
     */
    public Message(String id, String conversationId, String role, String content,
                   List<ToolCall> toolCalls, List<ToolResult> toolResults,
                   Integer inputTokens, Integer outputTokens, long timestamp) {
        this.id = id;
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
        this.toolResults = toolResults;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
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

    public List<ToolResult> getToolResults() {
        return toolResults;
    }

    public void setToolResults(List<ToolResult> toolResults) {
        this.toolResults = toolResults;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Integer inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Integer outputTokens) {
        this.outputTokens = outputTokens;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        Message that = (Message) obj;

        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
