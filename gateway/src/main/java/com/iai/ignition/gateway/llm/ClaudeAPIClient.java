package com.iai.ignition.gateway.llm;

import com.iai.ignition.common.llm.*;
import com.iai.ignition.common.model.Message;
import com.iai.ignition.common.model.ToolCall;
import com.inductiveautomation.ignition.common.gson.*;
import com.inductiveautomation.ignition.common.util.LoggerEx;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of IAILLMProvider for Claude API.
 * Follows HttpURLConnection pattern from SDK slack-notification example.
 */
public class ClaudeAPIClient implements IAILLMProvider {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.llm.ClaudeAPIClient");
    private static final String API_ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final Gson gson = new Gson();

    private final String apiKey;

    /**
     * Create a Claude API client with the given API key.
     *
     * @param apiKey The Claude API key from settings
     */
    public ClaudeAPIClient(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public LLMResponse sendMessage(LLMRequest request) throws LLMException {
        if (!isConfigured()) {
            throw new LLMException("Claude API client is not configured. API key is missing.");
        }

        try {
            // Build request JSON
            JsonObject requestJson = buildRequestJson(request);
            String requestBody = gson.toJson(requestJson);

            logger.debug("Sending request to Claude API: " + API_ENDPOINT);

            // Create HTTP connection
            URL url = new URL(API_ENDPOINT);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("anthropic-version", API_VERSION);
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("content-type", "application/json");
            conn.setDoOutput(true);

            // Write request body
            writeRequest(conn, requestBody);

            // Read response
            int responseCode = conn.getResponseCode();
            String responseBody;

            if (responseCode >= 200 && responseCode < 300) {
                responseBody = readResponse(conn.getInputStream());
                logger.debug("Claude API response received successfully");
            } else {
                responseBody = readResponse(conn.getErrorStream());
                logger.error("Claude API error response (code " + responseCode + "): " + responseBody);
                throw new LLMException("Claude API request failed with code " + responseCode + ": " + responseBody);
            }

            // Parse response
            return parseResponse(responseBody);

        } catch (IOException e) {
            logger.error("Error communicating with Claude API", e);
            throw new LLMException("Failed to communicate with Claude API", e);
        }
    }

    @Override
    public String getProviderName() {
        return "Claude";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Build the Claude API request JSON from LLMRequest.
     */
    private JsonObject buildRequestJson(LLMRequest request) {
        JsonObject json = new JsonObject();

        // Model and max_tokens
        json.addProperty("model", request.getModelName());
        json.addProperty("max_tokens", request.getMaxTokens());

        // System prompt
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            json.addProperty("system", request.getSystemPrompt());
        }

        // Messages
        JsonArray messages = new JsonArray();
        for (Message msg : request.getMessages()) {
            JsonObject messageObj = new JsonObject();
            messageObj.addProperty("role", msg.getRole());

            // Build content array
            JsonArray content = new JsonArray();

            // Add text content
            if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                JsonObject textContent = new JsonObject();
                textContent.addProperty("type", "text");
                textContent.addProperty("text", msg.getContent());
                content.add(textContent);
            }

            // Add tool_use content blocks
            if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                for (ToolCall toolCall : msg.getToolCalls()) {
                    JsonObject toolUse = new JsonObject();
                    toolUse.addProperty("type", "tool_use");
                    toolUse.addProperty("id", toolCall.getId());
                    toolUse.addProperty("name", toolCall.getName());
                    toolUse.add("input", gson.toJsonTree(toolCall.getInput()));
                    content.add(toolUse);
                }
            }

            // Add tool_result content blocks
            if (msg.getToolResults() != null && !msg.getToolResults().isEmpty()) {
                for (com.iai.ignition.common.model.ToolResult toolResult : msg.getToolResults()) {
                    JsonObject toolResultObj = new JsonObject();
                    toolResultObj.addProperty("type", "tool_result");
                    toolResultObj.addProperty("tool_use_id", toolResult.getToolCallId());
                    toolResultObj.addProperty("content", toolResult.getContent());
                    if (toolResult.isError()) {
                        toolResultObj.addProperty("is_error", true);
                    }
                    content.add(toolResultObj);
                }
            }

            messageObj.add("content", content);
            messages.add(messageObj);
        }
        json.add("messages", messages);

        // Tools
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            JsonArray tools = new JsonArray();
            for (LLMRequest.ToolDefinition tool : request.getTools()) {
                JsonObject toolObj = new JsonObject();
                toolObj.addProperty("name", tool.getName());
                toolObj.addProperty("description", tool.getDescription());
                toolObj.add("input_schema", gson.toJsonTree(tool.getInputSchema()));
                tools.add(toolObj);
            }
            json.add("tools", tools);
        }

        return json;
    }

    /**
     * Parse the Claude API response JSON into LLMResponse.
     */
    private LLMResponse parseResponse(String responseBody) throws LLMException {
        try {
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            // Extract content
            StringBuilder contentBuilder = new StringBuilder();
            List<ToolCall> toolCalls = new ArrayList<>();

            JsonArray content = json.getAsJsonArray("content");
            if (content != null) {
                for (JsonElement element : content) {
                    JsonObject block = element.getAsJsonObject();
                    String type = block.get("type").getAsString();

                    if ("text".equals(type)) {
                        String text = block.get("text").getAsString();
                        if (contentBuilder.length() > 0) {
                            contentBuilder.append("\n");
                        }
                        contentBuilder.append(text);
                    } else if ("tool_use".equals(type)) {
                        String id = block.get("id").getAsString();
                        String name = block.get("name").getAsString();
                        JsonObject input = block.getAsJsonObject("input");

                        Map<String, Object> inputMap = gson.fromJson(input, Map.class);
                        toolCalls.add(new ToolCall(id, name, inputMap));
                    }
                }
            }

            // Extract usage
            int inputTokens = 0;
            int outputTokens = 0;
            if (json.has("usage")) {
                JsonObject usage = json.getAsJsonObject("usage");
                inputTokens = usage.get("input_tokens").getAsInt();
                outputTokens = usage.get("output_tokens").getAsInt();
            }

            // Extract stop_reason
            String stopReason = json.has("stop_reason") ? json.get("stop_reason").getAsString() : null;

            return new LLMResponse(contentBuilder.toString(), toolCalls, inputTokens, outputTokens, stopReason);

        } catch (Exception e) {
            logger.error("Error parsing Claude API response", e);
            throw new LLMException("Failed to parse Claude API response", e);
        }
    }

    /**
     * Write request body to HTTP connection.
     * Follows pattern from SDK slack-notification example.
     */
    private void writeRequest(HttpURLConnection conn, String data) throws IOException {
        try (Writer writer = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write(data);
        }
    }

    /**
     * Read response from HTTP connection input stream.
     */
    private String readResponse(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }
}
