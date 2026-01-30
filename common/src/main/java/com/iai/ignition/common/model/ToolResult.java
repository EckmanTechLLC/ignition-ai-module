package com.iai.ignition.common.model;

import java.io.Serializable;

/**
 * Represents the result of a tool execution.
 * Used for JSON serialization in Message.toolResults field.
 */
public class ToolResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private String toolCallId;
    private String content;
    private boolean isError;

    /**
     * No-arg constructor for serialization.
     */
    public ToolResult() {
    }

    /**
     * Full constructor for creating tool results.
     */
    public ToolResult(String toolCallId, String content, boolean isError) {
        this.toolCallId = toolCallId;
        this.content = content;
        this.isError = isError;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isError() {
        return isError;
    }

    public void setError(boolean error) {
        isError = error;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        ToolResult that = (ToolResult) obj;

        return toolCallId != null ? toolCallId.equals(that.toolCallId) : that.toolCallId == null;
    }

    @Override
    public int hashCode() {
        return toolCallId != null ? toolCallId.hashCode() : 0;
    }
}
