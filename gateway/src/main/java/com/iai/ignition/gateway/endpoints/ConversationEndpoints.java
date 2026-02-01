package com.iai.ignition.gateway.endpoints;

import com.iai.ignition.common.llm.LLMRequest;
import com.iai.ignition.common.llm.LLMResponse;
import com.iai.ignition.common.model.Conversation;
import com.iai.ignition.common.model.Message;
import com.iai.ignition.common.model.ToolCall;
import com.iai.ignition.common.model.ToolResult;
import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.database.ConversationDAO;
import com.iai.ignition.gateway.database.MessageDAO;
import com.iai.ignition.gateway.llm.ClaudeAPIClient;
import com.iai.ignition.gateway.records.IAISettings;
import com.iai.ignition.gateway.tools.ToolRegistry;
import com.inductiveautomation.ignition.common.gson.Gson;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.gson.JsonParser;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.dataroutes.HttpMethod;
import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import com.inductiveautomation.ignition.gateway.dataroutes.RouteGroup;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.util.*;

/**
 * RPC endpoints for conversation management and AI interaction.
 * Exposed to Perspective components via REST API.
 */
public final class ConversationEndpoints {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.endpoints.ConversationEndpoints");
    private static final int MAX_TOOL_ITERATIONS = 10; // Prevent infinite tool loops
    private static final Gson gson = new Gson();

    private ConversationEndpoints() {
        // Private constructor for utility class
    }

    /**
     * Mount all conversation-related routes.
     *
     * @param routes RouteGroup to mount routes on
     */
    public static void mountRoutes(RouteGroup routes) {
        logger.info("Mounting conversation endpoints");

        // Test endpoint to verify routing works
        routes.newRoute("/test")
            .type(RouteGroup.TYPE_JSON)
            .handler((req, res) -> {
                logger.info("=== TEST endpoint called! ===");
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "Routing is working!");
                return response;
            })
            .mount();

        logger.info("Mounted route: GET /test");

        // POST /sendMessage - Send a message and get AI response
        routes.newRoute("/sendMessage")
            .method(HttpMethod.POST)
            .type(RouteGroup.TYPE_JSON)
            .handler(ConversationEndpoints::sendMessage)
            .mount();

        logger.info("Mounted route: POST /sendMessage");

        // GET /getConversation/:id - Retrieve a conversation with all messages
        routes.newRoute("/getConversation/:id")
            .type(RouteGroup.TYPE_JSON)
            .handler((req, res) -> getConversation(req, res, req.getParameter("id")))
            .mount();

        // GET /listConversations - List conversations for a user/project
        routes.newRoute("/listConversations")
            .type(RouteGroup.TYPE_JSON)
            .handler(ConversationEndpoints::listConversations)
            .mount();

        // DELETE /deleteConversation/:id - Delete a conversation
        routes.newRoute("/deleteConversation/:id")
            .method(HttpMethod.DELETE)
            .type(RouteGroup.TYPE_JSON)
            .handler((req, res) -> deleteConversation(req, res, req.getParameter("id")))
            .mount();

        // GET /exportConversation/:id - Export conversation as JSON or Markdown
        routes.newRoute("/exportConversation/:id")
            .type(RouteGroup.TYPE_JSON)
            .handler((req, res) -> exportConversation(req, res, req.getParameter("id")))
            .mount();

        logger.info("Conversation endpoints mounted successfully");
    }

    /**
     * Send a message and get AI response.
     * This is the main endpoint for chat interaction.
     */
    private static JsonObject sendMessage(RequestContext req, HttpServletResponse res) {
        logger.info("=== sendMessage handler called! ===");
        logger.info("Request method: " + req.getRequest().getMethod());
        logger.info("Request URI: " + req.getRequest().getRequestURI());

        JsonObject response = new JsonObject();
        GatewayContext context = req.getGatewayContext();

        try {
            // Read and parse request body using RequestContext.readBody()
            String requestBodyString = req.readBody();
            JsonObject requestBody = new JsonParser().parse(requestBodyString).getAsJsonObject();

            // Extract parameters
            String conversationId = requestBody.has("conversationId") && !requestBody.get("conversationId").isJsonNull()
                ? requestBody.get("conversationId").getAsString() : null;
            String userName = requestBody.has("userName") ? requestBody.get("userName").getAsString() : null;
            String projectName = requestBody.get("projectName").getAsString();
            String message = requestBody.get("message").getAsString();

            // Load settings
            IAISettings settings = context.getLocalPersistenceInterface().find(IAISettings.META, 0L);
            if (settings == null) {
                throw new IllegalStateException("IAI settings not found");
            }

            // Validate configuration
            if (settings.getApiKey() == null || settings.getApiKey().isEmpty()) {
                throw new IllegalStateException("Claude API key not configured");
            }
            if (settings.getDatabaseConnection() == null || settings.getDatabaseConnection().isEmpty()) {
                throw new IllegalStateException("Database connection not configured");
            }

            String dbConnection = settings.getDatabaseConnection();

            // Create or load conversation
            Conversation conversation;
            if (conversationId == null || conversationId.isEmpty()) {
                // Create new conversation
                conversation = new Conversation();
                conversation.setId(UUID.randomUUID().toString());
                conversation.setUserName(userName);
                conversation.setProjectName(projectName);
                conversation.setTitle(generateConversationTitle(message));
                conversation.setCreatedAt(System.currentTimeMillis());
                conversation.setLastUpdatedAt(System.currentTimeMillis());

                ConversationDAO.create(context.getDatasourceManager(), dbConnection, conversation);
                logger.info("Created new conversation: " + conversation.getId());
            } else {
                // Load existing conversation
                conversation = ConversationDAO.findById(context.getDatasourceManager(), dbConnection, conversationId);
                if (conversation == null) {
                    throw new IllegalArgumentException("Conversation not found: " + conversationId);
                }

                // Update last updated time
                conversation.setLastUpdatedAt(System.currentTimeMillis());
                ConversationDAO.update(context.getDatasourceManager(), dbConnection, conversation);
            }

            // Save user message
            Message userMessage = new Message();
            userMessage.setId(UUID.randomUUID().toString());
            userMessage.setConversationId(conversation.getId());
            userMessage.setRole("user");
            userMessage.setContent(message);
            userMessage.setTimestamp(System.currentTimeMillis());

            MessageDAO.create(context.getDatasourceManager(), dbConnection, userMessage);

            // Initialize Claude API client
            ClaudeAPIClient claudeClient = new ClaudeAPIClient(settings.getApiKey());

            // Initialize tool registry
            ToolRegistry toolRegistry = new ToolRegistry(context, settings);

            // Process message with AI (may involve multiple tool calls)
            Message assistantMessage = processWithAI(
                context,
                settings,
                claudeClient,
                toolRegistry,
                conversation,
                dbConnection
            );

            // Build response
            response.addProperty("success", true);
            response.addProperty("conversationId", conversation.getId());
            response.addProperty("messageId", assistantMessage.getId());
            response.addProperty("content", assistantMessage.getContent());
            response.addProperty("inputTokens", assistantMessage.getInputTokens());
            response.addProperty("outputTokens", assistantMessage.getOutputTokens());

            // Include tool calls if any
            if (assistantMessage.getToolCalls() != null && !assistantMessage.getToolCalls().isEmpty()) {
                JsonArray toolCallsArray = new JsonArray();
                for (ToolCall tc : assistantMessage.getToolCalls()) {
                    JsonObject toolCallObj = new JsonObject();
                    toolCallObj.addProperty("id", tc.getId());
                    toolCallObj.addProperty("name", tc.getName());
                    toolCallObj.addProperty("input", tc.getInput().toString());
                    toolCallsArray.add(toolCallObj);
                }
                response.add("toolCalls", toolCallsArray);
            }

        } catch (Exception e) {
            logger.error("Error in sendMessage endpoint", e);
            response.addProperty("success", false);
            response.addProperty("error", e.getMessage());
            res.setStatus(500);
        }

        return response;
    }

    /**
     * Process message with AI, handling tool calls in a loop.
     * Public so it can be called from ComponentModelDelegate.
     */
    public static Message processWithAI(
        GatewayContext context,
        IAISettings settings,
        ClaudeAPIClient claudeClient,
        ToolRegistry toolRegistry,
        Conversation conversation,
        String dbConnection
    ) throws Exception {

        // Load conversation history
        List<Message> history = MessageDAO.listByConversation(
            context.getDatasourceManager(),
            dbConnection,
            conversation.getId(),
            settings.getMaxConversationHistoryMessages()
        );

        // Limit history to configured max
        int maxHistory = settings.getMaxConversationHistoryMessages();
        if (history.size() > maxHistory) {
            history = history.subList(history.size() - maxHistory, history.size());
        }

        // Build system prompt
        String systemPrompt = buildSystemPrompt(settings, conversation, toolRegistry);

        // Clean history for API: strip toolCalls/toolResults (they're for audit only)
        // Claude doesn't need to see previous tool use in conversation history
        List<Message> llmMessages = new ArrayList<>();
        for (Message msg : history) {
            Message cleanMsg = new Message();
            cleanMsg.setId(msg.getId());
            cleanMsg.setConversationId(msg.getConversationId());
            cleanMsg.setRole(msg.getRole());
            cleanMsg.setContent(msg.getContent());
            cleanMsg.setTimestamp(msg.getTimestamp());
            // Don't copy toolCalls or toolResults - they shouldn't be in API requests
            llmMessages.add(cleanMsg);
        }

        // Build tool definitions
        List<LLMRequest.ToolDefinition> toolDefinitions = new ArrayList<>();
        for (Map<String, Object> toolDef : toolRegistry.getToolDefinitions()) {
            LLMRequest.ToolDefinition td = new LLMRequest.ToolDefinition();
            td.setName((String) toolDef.get("name"));
            td.setDescription((String) toolDef.get("description"));
            // Convert JsonObject to Map for input_schema
            Object inputSchema = toolDef.get("input_schema");
            if (inputSchema instanceof JsonObject) {
                String json = gson.toJson(inputSchema);
                td.setInputSchema(gson.fromJson(json, Map.class));
            } else if (inputSchema instanceof Map) {
                td.setInputSchema((Map<String, Object>) inputSchema);
            }
            toolDefinitions.add(td);
        }

        // Tool execution loop
        int iteration = 0;
        LLMResponse llmResponse = null;
        List<ToolCall> allToolCalls = new ArrayList<>();
        List<ToolResult> allToolResults = new ArrayList<>();

        while (iteration < MAX_TOOL_ITERATIONS) {
            iteration++;

            // Build request
            LLMRequest request = new LLMRequest();
            request.setModelName(settings.getModelName());
            request.setMaxTokens(4096);
            request.setSystemPrompt(systemPrompt);
            request.setMessages(llmMessages);
            request.setTools(toolDefinitions);

            // Call Claude API
            llmResponse = claudeClient.sendMessage(request);

            // Check if there are tool calls
            if (llmResponse.getToolCalls() == null || llmResponse.getToolCalls().isEmpty()) {
                // No tool calls, we have final response
                break;
            }

            logger.debug("Processing " + llmResponse.getToolCalls().size() + " tool calls");

            // Execute tool calls
            List<ToolResult> toolResults = new ArrayList<>();
            for (ToolCall toolCall : llmResponse.getToolCalls()) {
                allToolCalls.add(toolCall);

                ToolResult result = new ToolResult();
                result.setToolCallId(toolCall.getId());

                try {
                    // Execute tool - convert Map to JsonObject
                    String inputJson = gson.toJson(toolCall.getInput());
                    JsonObject inputParams = new JsonParser().parse(inputJson).getAsJsonObject();
                    JsonObject toolResult = toolRegistry.executeTool(toolCall.getName(), inputParams);

                    result.setContent(toolResult.toString());
                    result.setError(false);
                    logger.debug("Tool " + toolCall.getName() + " executed successfully");

                } catch (Exception e) {
                    logger.error("Error executing tool " + toolCall.getName(), e);
                    result.setContent("Error: " + e.getMessage());
                    result.setError(true);
                }

                toolResults.add(result);
                allToolResults.add(result);
            }

            // Add assistant message with tool calls to history
            Message assistantMsg = new Message();
            assistantMsg.setId(UUID.randomUUID().toString());
            assistantMsg.setConversationId(conversation.getId());
            assistantMsg.setRole("assistant");

            // Claude API requires non-empty content for intermediate messages
            // If response has no text but has tool calls, use placeholder
            String content = llmResponse.getContent();
            if ((content == null || content.isEmpty()) &&
                llmResponse.getToolCalls() != null && !llmResponse.getToolCalls().isEmpty()) {
                content = "[Using tools]";
            }
            assistantMsg.setContent(content);
            // CRITICAL: Must include toolCalls so tool_result blocks can reference them
            assistantMsg.setToolCalls(llmResponse.getToolCalls());
            assistantMsg.setTimestamp(System.currentTimeMillis());
            llmMessages.add(assistantMsg);

            // Add tool results as a single user message with tool_result blocks
            Message toolResultsMsg = new Message();
            toolResultsMsg.setId(UUID.randomUUID().toString());
            toolResultsMsg.setConversationId(conversation.getId());
            toolResultsMsg.setRole("user");
            toolResultsMsg.setToolResults(toolResults);
            toolResultsMsg.setTimestamp(System.currentTimeMillis());
            llmMessages.add(toolResultsMsg);
        }

        if (iteration >= MAX_TOOL_ITERATIONS) {
            logger.warn("Max tool iterations reached, returning partial response");
        }

        // Save assistant message
        Message assistantMessage = new Message();
        assistantMessage.setId(UUID.randomUUID().toString());
        assistantMessage.setConversationId(conversation.getId());
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(llmResponse.getContent());
        // Save toolCalls for audit/display purposes only
        assistantMessage.setToolCalls(allToolCalls.isEmpty() ? null : allToolCalls);
        // Don't save toolResults - they belong to user messages, not assistant messages
        assistantMessage.setInputTokens(llmResponse.getInputTokens());
        assistantMessage.setOutputTokens(llmResponse.getOutputTokens());
        assistantMessage.setTimestamp(System.currentTimeMillis());

        MessageDAO.create(context.getDatasourceManager(), dbConnection, assistantMessage);

        return assistantMessage;
    }

    /**
     * Build system prompt with context variables injected.
     */
    /**
     * Get the default system prompt.
     */
    private static String getDefaultSystemPrompt() {
        return "You are Ignition AI, an AI assistant integrated into Inductive Automation's Ignition SCADA platform.\n\n" +
            "## Your Role\n" +
            "You help users understand, explore, and explain existing Ignition systems. You provide read-only analysis " +
            "and never modify configurations, tags, or code.\n\n" +
            "## Available Tools\n" +
            "You have access to tools for reading project resources, querying tags and alarms, and analyzing system data.\n\n" +
            "## Using Tools\n" +
            "Use the available tools to retrieve actual system data. When users ask about system information, " +
            "always use tools to get current, accurate data rather than making assumptions. This includes " +
            "follow-up questions - verify data with tools each time.\n\n" +
            "## Response Style\n" +
            "Be clear, concise, and technical. Cite specific file paths and line numbers when referencing code.";
    }

    private static String buildSystemPrompt(IAISettings settings, Conversation conversation, ToolRegistry toolRegistry) {
        // Use custom prompt from settings, or fall back to hardcoded default if empty
        String prompt = settings.getSystemPrompt();
        if (prompt == null || prompt.trim().isEmpty()) {
            prompt = getDefaultSystemPrompt();
        }

        // Inject variables
        prompt = prompt.replace("{PROJECT_NAME}", conversation.getProjectName() != null ? conversation.getProjectName() : "Unknown");
        prompt = prompt.replace("{USER_NAME}", conversation.getUserName() != null ? conversation.getUserName() : "Anonymous");

        // Build available tools list
        StringBuilder toolsList = new StringBuilder();
        for (String toolName : toolRegistry.getAllTools().keySet()) {
            toolsList.append("- ").append(toolName).append("\n");
        }
        prompt = prompt.replace("{AVAILABLE_TOOLS}", toolsList.toString());

        return prompt;
    }

    /**
     * Generate a conversation title from the first message.
     */
    private static String generateConversationTitle(String firstMessage) {
        // Simple title generation - take first 50 chars
        if (firstMessage.length() <= 50) {
            return firstMessage;
        }
        return firstMessage.substring(0, 47) + "...";
    }

    /**
     * Get a conversation with all its messages.
     */
    private static JsonObject getConversation(RequestContext req, HttpServletResponse res, String conversationId) {
        JsonObject response = new JsonObject();
        GatewayContext context = req.getGatewayContext();

        try {
            // Validate conversationId parameter
            if (conversationId == null || conversationId.isEmpty() || conversationId.equals("null") || conversationId.equals("undefined")) {
                throw new IllegalArgumentException("Invalid conversation ID. Cannot load conversation with null or empty ID.");
            }

            IAISettings settings = context.getLocalPersistenceInterface().find(IAISettings.META, 0L);
            if (settings == null) {
                throw new IllegalStateException("IAI settings not found");
            }

            String dbConnection = settings.getDatabaseConnection();
            if (dbConnection == null || dbConnection.isEmpty()) {
                throw new IllegalStateException("Database connection not configured");
            }

            // Load conversation
            Conversation conversation = ConversationDAO.findById(
                context.getDatasourceManager(),
                dbConnection,
                conversationId
            );

            if (conversation == null) {
                throw new IllegalArgumentException("Conversation not found: " + conversationId);
            }

            // Load messages (use large limit to get all messages for export)
            List<Message> messages = MessageDAO.listByConversation(
                context.getDatasourceManager(),
                dbConnection,
                conversationId,
                10000
            );

            // Build response
            response.addProperty("id", conversation.getId());
            response.addProperty("userName", conversation.getUserName());
            response.addProperty("projectName", conversation.getProjectName());
            response.addProperty("title", conversation.getTitle());
            response.addProperty("createdAt", conversation.getCreatedAt());
            response.addProperty("lastUpdatedAt", conversation.getLastUpdatedAt());

            JsonArray messagesArray = new JsonArray();
            for (Message msg : messages) {
                JsonObject msgObj = new JsonObject();
                msgObj.addProperty("id", msg.getId());
                msgObj.addProperty("role", msg.getRole());
                msgObj.addProperty("content", msg.getContent());
                msgObj.addProperty("timestamp", msg.getTimestamp());

                if (msg.getInputTokens() != null) {
                    msgObj.addProperty("inputTokens", msg.getInputTokens());
                }
                if (msg.getOutputTokens() != null) {
                    msgObj.addProperty("outputTokens", msg.getOutputTokens());
                }

                messagesArray.add(msgObj);
            }

            response.add("messages", messagesArray);
            response.addProperty("messageCount", messages.size());
            response.addProperty("success", true);

        } catch (Exception e) {
            logger.error("Error in getConversation endpoint", e);
            response.addProperty("success", false);
            response.addProperty("error", e.getMessage());
            res.setStatus(500);
        }

        return response;
    }

    /**
     * List conversations for a user and/or project.
     */
    private static JsonObject listConversations(RequestContext req, HttpServletResponse res) {
        JsonObject response = new JsonObject();
        GatewayContext context = req.getGatewayContext();

        try {
            // Get query parameters
            String userName = req.getRequest().getParameter("userName");
            String projectName = req.getRequest().getParameter("projectName");

            IAISettings settings = context.getLocalPersistenceInterface().find(IAISettings.META, 0L);
            if (settings == null) {
                throw new IllegalStateException("IAI settings not found");
            }

            String dbConnection = settings.getDatabaseConnection();
            if (dbConnection == null || dbConnection.isEmpty()) {
                throw new IllegalStateException("Database connection not configured");
            }

            // Load conversations based on filters (limit to most recent 100)
            List<Conversation> conversations;
            if (userName != null && !userName.isEmpty()) {
                conversations = ConversationDAO.listByUser(
                    context.getDatasourceManager(),
                    dbConnection,
                    userName,
                    100
                );
            } else if (projectName != null && !projectName.isEmpty()) {
                conversations = ConversationDAO.listByProject(
                    context.getDatasourceManager(),
                    dbConnection,
                    projectName,
                    100
                );
            } else {
                // Return empty list if no filter specified
                conversations = new ArrayList<>();
            }

            // Build response
            JsonArray conversationsArray = new JsonArray();
            for (Conversation conv : conversations) {
                JsonObject convObj = new JsonObject();
                convObj.addProperty("id", conv.getId());
                convObj.addProperty("userName", conv.getUserName());
                convObj.addProperty("projectName", conv.getProjectName());
                convObj.addProperty("title", conv.getTitle());
                convObj.addProperty("createdAt", conv.getCreatedAt());
                convObj.addProperty("lastUpdatedAt", conv.getLastUpdatedAt());

                // Get message count
                int messageCount = MessageDAO.countByConversation(
                    context.getDatasourceManager(),
                    dbConnection,
                    conv.getId()
                );
                convObj.addProperty("messageCount", messageCount);

                conversationsArray.add(convObj);
            }

            response.add("conversations", conversationsArray);
            response.addProperty("count", conversations.size());
            response.addProperty("success", true);

        } catch (Exception e) {
            logger.error("Error in listConversations endpoint", e);
            response.addProperty("success", false);
            response.addProperty("error", e.getMessage());
            res.setStatus(500);
        }

        return response;
    }

    /**
     * Delete a conversation and all its messages.
     */
    private static JsonObject deleteConversation(RequestContext req, HttpServletResponse res, String conversationId) {
        JsonObject response = new JsonObject();
        GatewayContext context = req.getGatewayContext();

        try {
            IAISettings settings = context.getLocalPersistenceInterface().find(IAISettings.META, 0L);
            if (settings == null) {
                throw new IllegalStateException("IAI settings not found");
            }

            String dbConnection = settings.getDatabaseConnection();
            if (dbConnection == null || dbConnection.isEmpty()) {
                throw new IllegalStateException("Database connection not configured");
            }

            // Delete conversation (messages will cascade due to FK constraint)
            boolean deleted = ConversationDAO.delete(
                context.getDatasourceManager(),
                dbConnection,
                conversationId
            );

            if (deleted) {
                response.addProperty("success", true);
                response.addProperty("deletedId", conversationId);
                logger.info("Deleted conversation: " + conversationId);
            } else {
                response.addProperty("success", false);
                response.addProperty("error", "Conversation not found or already deleted");
            }

        } catch (Exception e) {
            logger.error("Error in deleteConversation endpoint", e);
            response.addProperty("success", false);
            response.addProperty("error", e.getMessage());
            res.setStatus(500);
        }

        return response;
    }

    /**
     * Export a conversation as JSON or Markdown.
     */
    private static JsonObject exportConversation(RequestContext req, HttpServletResponse res, String conversationId) {
        JsonObject response = new JsonObject();
        GatewayContext context = req.getGatewayContext();

        try {
            String format = req.getRequest().getParameter("format");
            if (format == null || format.isEmpty()) {
                format = "json";
            }

            IAISettings settings = context.getLocalPersistenceInterface().find(IAISettings.META, 0L);
            if (settings == null) {
                throw new IllegalStateException("IAI settings not found");
            }

            String dbConnection = settings.getDatabaseConnection();
            if (dbConnection == null || dbConnection.isEmpty()) {
                throw new IllegalStateException("Database connection not configured");
            }

            // Load conversation and messages
            Conversation conversation = ConversationDAO.findById(
                context.getDatasourceManager(),
                dbConnection,
                conversationId
            );

            if (conversation == null) {
                throw new IllegalArgumentException("Conversation not found: " + conversationId);
            }

            List<Message> messages = MessageDAO.listByConversation(
                context.getDatasourceManager(),
                dbConnection,
                conversationId,
                10000
            );

            // Export based on format
            if ("markdown".equalsIgnoreCase(format)) {
                String markdown = exportAsMarkdown(conversation, messages);
                response.addProperty("format", "markdown");
                response.addProperty("content", markdown);
            } else {
                // Default to JSON
                JsonObject exportData = exportAsJson(conversation, messages);
                response.addProperty("format", "json");
                response.add("content", exportData);
            }

            response.addProperty("success", true);

        } catch (Exception e) {
            logger.error("Error in exportConversation endpoint", e);
            response.addProperty("success", false);
            response.addProperty("error", e.getMessage());
            res.setStatus(500);
        }

        return response;
    }

    /**
     * Export conversation as JSON.
     */
    private static JsonObject exportAsJson(Conversation conversation, List<Message> messages) {
        JsonObject export = new JsonObject();
        export.addProperty("id", conversation.getId());
        export.addProperty("userName", conversation.getUserName());
        export.addProperty("projectName", conversation.getProjectName());
        export.addProperty("title", conversation.getTitle());
        export.addProperty("createdAt", conversation.getCreatedAt());
        export.addProperty("lastUpdatedAt", conversation.getLastUpdatedAt());

        JsonArray messagesArray = new JsonArray();
        for (Message msg : messages) {
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("id", msg.getId());
            msgObj.addProperty("role", msg.getRole());
            msgObj.addProperty("content", msg.getContent());
            msgObj.addProperty("timestamp", msg.getTimestamp());

            if (msg.getInputTokens() != null) {
                msgObj.addProperty("inputTokens", msg.getInputTokens());
            }
            if (msg.getOutputTokens() != null) {
                msgObj.addProperty("outputTokens", msg.getOutputTokens());
            }

            messagesArray.add(msgObj);
        }

        export.add("messages", messagesArray);
        return export;
    }

    /**
     * Export conversation as Markdown.
     */
    private static String exportAsMarkdown(Conversation conversation, List<Message> messages) {
        StringBuilder md = new StringBuilder();

        md.append("# ").append(conversation.getTitle()).append("\n\n");
        md.append("**Project:** ").append(conversation.getProjectName()).append("\n");
        if (conversation.getUserName() != null) {
            md.append("**User:** ").append(conversation.getUserName()).append("\n");
        }
        md.append("**Created:** ").append(new Date(conversation.getCreatedAt())).append("\n");
        md.append("\n---\n\n");

        for (Message msg : messages) {
            if ("user".equals(msg.getRole())) {
                md.append("## 👤 User\n\n");
            } else if ("assistant".equals(msg.getRole())) {
                md.append("## 🤖 Assistant\n\n");
                if (msg.getInputTokens() != null && msg.getOutputTokens() != null) {
                    md.append("*Tokens: ").append(msg.getInputTokens()).append(" in / ")
                        .append(msg.getOutputTokens()).append(" out*\n\n");
                }
            }

            md.append(msg.getContent()).append("\n\n");
            md.append("---\n\n");
        }

        return md.toString();
    }
}
