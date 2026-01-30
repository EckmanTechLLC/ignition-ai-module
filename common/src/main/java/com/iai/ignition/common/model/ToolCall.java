package com.iai.ignition.common.model;

import java.io.Serializable;
import java.util.Map;

/**
 * Represents a tool invocation request from Claude.
 * Used for JSON serialization in Message.toolCalls field.
 */
public class ToolCall implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private Map<String, Object> input;

    /**
     * No-arg constructor for serialization.
     */
    public ToolCall() {
    }

    /**
     * Full constructor for creating tool calls.
     */
    public ToolCall(String id, String name, Map<String, Object> input) {
        this.id = id;
        this.name = name;
        this.input = input;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        ToolCall that = (ToolCall) obj;

        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
