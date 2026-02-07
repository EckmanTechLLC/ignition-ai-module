package com.iai.ignition.gateway.tools.conversation;

import com.iai.ignition.common.model.Message;
import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.database.MessageDAO;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.util.List;

/**
 * Tool for querying conversation history/memory.
 * Allows Claude to recall what was said earlier in the conversation.
 */
public class QueryConversationMemoryTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.conversation.QueryConversationMemoryTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;
    private String currentConversationId;

    public QueryConversationMemoryTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    /**
     * Set the current conversation ID for this tool instance.
     * Called by ToolRegistry before each use.
     */
    public void setConversationId(String conversationId) {
        this.currentConversationId = conversationId;
    }

    @Override
    public String getName() {
        return "query_conversation_memory";
    }

    @Override
    public String getDescription() {
        return "Query conversation history to recall what was said earlier. " +
               "Use this to remember previous messages, user requests, or information discussed. " +
               "Searches recent conversation messages for relevant content.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject query = new JsonObject();
        query.addProperty("type", "string");
        query.addProperty("description", "What to search for in conversation history (e.g., 'ToggleMe', 'tag value', 'previous request')");
        properties.add("query", query);

        JsonObject lookback = new JsonObject();
        lookback.addProperty("type", "integer");
        lookback.addProperty("description", "Number of recent messages to search (default: 20)");
        lookback.addProperty("default", 20);
        properties.add("lookback", lookback);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("query");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        if (currentConversationId == null || currentConversationId.isEmpty()) {
            throw new IllegalStateException("Conversation ID not set for memory query");
        }

        String query = params.get("query").getAsString().toLowerCase();
        int lookback = params.has("lookback") ? params.get("lookback").getAsInt() : 20;

        logger.debug("Querying conversation memory: query=" + query + ", lookback=" + lookback);

        String dbConnection = settings.getDatabaseConnection();

        // Load recent messages
        List<Message> messages = MessageDAO.listByConversation(
            gatewayContext.getDatasourceManager(),
            dbConnection,
            currentConversationId,
            lookback
        );

        // Search for relevant messages
        JsonArray results = new JsonArray();
        int matchCount = 0;

        for (Message msg : messages) {
            String content = msg.getContent();
            if (content != null && content.toLowerCase().contains(query)) {
                JsonObject match = new JsonObject();
                match.addProperty("role", msg.getRole());
                match.addProperty("content", content);
                match.addProperty("timestamp", msg.getTimestamp());
                results.add(match);
                matchCount++;
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("query", query);
        result.addProperty("matches", matchCount);
        result.addProperty("searched_messages", messages.size());
        result.add("results", results);

        if (matchCount == 0) {
            result.addProperty("message", "No matches found in recent conversation history");
        }

        logger.debug("Memory query found " + matchCount + " matches");

        return result;
    }
}
