package com.iai.ignition.common.llm;

import com.iai.ignition.common.model.Message;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Represents a request to an LLM provider.
 */
public class LLMRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String modelName;
    private int maxTokens;
    private String systemPrompt;
    private List<Message> messages;
    private List<ToolDefinition> tools;

    /**
     * No-arg constructor for serialization.
     */
    public LLMRequest() {
    }

    /**
     * Full constructor.
     */
    public LLMRequest(String modelName, int maxTokens, String systemPrompt, List<Message> messages, List<ToolDefinition> tools) {
        this.modelName = modelName;
        this.maxTokens = maxTokens;
        this.systemPrompt = systemPrompt;
        this.messages = messages;
        this.tools = tools;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public List<ToolDefinition> getTools() {
        return tools;
    }

    public void setTools(List<ToolDefinition> tools) {
        this.tools = tools;
    }

    /**
     * Represents a tool definition for the LLM.
     */
    public static class ToolDefinition implements Serializable {
        private static final long serialVersionUID = 1L;

        private String name;
        private String description;
        private Map<String, Object> inputSchema;

        public ToolDefinition() {
        }

        public ToolDefinition(String name, String description, Map<String, Object> inputSchema) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Map<String, Object> getInputSchema() {
            return inputSchema;
        }

        public void setInputSchema(Map<String, Object> inputSchema) {
            this.inputSchema = inputSchema;
        }
    }
}
