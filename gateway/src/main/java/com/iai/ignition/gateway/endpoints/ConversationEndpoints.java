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
import com.iai.ignition.gateway.util.TokenCounter;
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

            // Extract compaction settings (with defaults)
            boolean enableAutoCompaction = requestBody.has("enableAutoCompaction")
                ? requestBody.get("enableAutoCompaction").getAsBoolean() : true;
            int compactionTokenThreshold = requestBody.has("compactionTokenThreshold")
                ? requestBody.get("compactionTokenThreshold").getAsInt() : 180000;
            int compactToRecentMessages = requestBody.has("compactToRecentMessages")
                ? requestBody.get("compactToRecentMessages").getAsInt() : 30;

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

            // Calculate estimated token count for user visibility
            List<Message> allMessages = MessageDAO.listByConversation(
                context.getDatasourceManager(),
                dbConnection,
                conversation.getId(),
                settings.getMaxConversationHistoryMessages()
            );
            int estimatedTokens = TokenCounter.estimateTokens(allMessages);

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
                dbConnection,
                enableAutoCompaction,
                compactionTokenThreshold,
                compactToRecentMessages
            );

            // Build response
            response.addProperty("success", true);
            response.addProperty("conversationId", conversation.getId());
            response.addProperty("messageId", assistantMessage.getId());
            response.addProperty("content", assistantMessage.getContent());
            response.addProperty("inputTokens", assistantMessage.getInputTokens());
            response.addProperty("outputTokens", assistantMessage.getOutputTokens());
            response.addProperty("estimatedTokens", estimatedTokens);

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
        String dbConnection,
        boolean enableAutoCompaction,
        int compactionTokenThreshold,
        int compactToRecentMessages
    ) throws Exception {

        // Load conversation history
        List<Message> allMessages = MessageDAO.listByConversation(
            context.getDatasourceManager(),
            dbConnection,
            conversation.getId(),
            settings.getMaxConversationHistoryMessages()
        );

        // Check for most recent summary message to avoid reloading compacted messages
        Message latestSummary = null;
        int summaryIndex = -1;
        for (int i = allMessages.size() - 1; i >= 0; i--) {
            Message msg = allMessages.get(i);
            if (msg.getContent() != null &&
                msg.getContent().startsWith("[CONVERSATION SUMMARY")) {
                latestSummary = msg;
                summaryIndex = i;
                logger.info("Found existing summary at message index " + i + ", using compacted history");
                break;
            }
        }

        // Build history: if summary exists, use [summary] + [messages after summary], else use all
        List<Message> history;
        if (latestSummary != null && summaryIndex < allMessages.size() - 1) {
            // Use summary + recent messages after it
            history = new ArrayList<>();
            history.add(latestSummary);
            history.addAll(allMessages.subList(summaryIndex + 1, allMessages.size()));
            logger.info("Using compacted history: 1 summary + " + (allMessages.size() - summaryIndex - 1) + " recent messages");
        } else if (latestSummary != null && summaryIndex == allMessages.size() - 1) {
            // Summary is the last message (edge case)
            history = new ArrayList<>();
            history.add(latestSummary);
            logger.info("Using only summary (no messages after it yet)");
        } else {
            // No summary, use all messages
            history = allMessages;
            int maxHistory = settings.getMaxConversationHistoryMessages();
            if (history.size() > maxHistory) {
                history = history.subList(history.size() - maxHistory, history.size());
            }
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
            // Copy token counts for accurate compaction threshold calculation
            cleanMsg.setInputTokens(msg.getInputTokens());
            cleanMsg.setOutputTokens(msg.getOutputTokens());
            // Don't copy toolCalls or toolResults - they shouldn't be in API requests
            llmMessages.add(cleanMsg);
        }

        // Calculate actual token count from stored API responses
        int systemPromptTokens = TokenCounter.estimateSystemPromptTokens(systemPrompt);

        // Sum actual token counts from previous messages (stored from Claude API responses)
        int messageTokens = 0;
        for (Message msg : llmMessages) {
            if (msg.getInputTokens() != null) {
                messageTokens += msg.getInputTokens();
            }
            if (msg.getOutputTokens() != null) {
                messageTokens += msg.getOutputTokens();
            }
        }

        int actualTotalTokens = systemPromptTokens + messageTokens;

        logger.debug("Actual tokens - System: " + systemPromptTokens + " (estimated), Messages: " + messageTokens + " (from API), Total: " + actualTotalTokens);

        // Automatic compaction if approaching context limit
        int contextLimit = 200000; // Claude context limit

        if (enableAutoCompaction && actualTotalTokens > compactionTokenThreshold && llmMessages.size() > compactToRecentMessages) {
            logger.info("Triggering automatic compaction - Total tokens: " + actualTotalTokens + " (threshold: " + compactionTokenThreshold + ")");

            try {
                // Split messages: old (to summarize) vs recent (keep full)
                List<Message> oldMessages = llmMessages.subList(0, llmMessages.size() - compactToRecentMessages);
                List<Message> recentMessages = llmMessages.subList(llmMessages.size() - compactToRecentMessages, llmMessages.size());

                logger.info("Compacting " + oldMessages.size() + " old messages, keeping " + recentMessages.size() + " recent messages");

                // Generate summary of old messages
                String summary = generateConversationSummary(claudeClient, settings, oldMessages);

                // Create summary message and save to database for future use
                Message summaryMessage = new Message();
                summaryMessage.setId(UUID.randomUUID().toString());
                summaryMessage.setConversationId(conversation.getId());
                summaryMessage.setRole("user"); // Claude only accepts "user" or "assistant"
                summaryMessage.setContent("[CONVERSATION SUMMARY - Previous " + oldMessages.size() + " messages condensed]\n\n" + summary);
                summaryMessage.setTimestamp(System.currentTimeMillis());

                // Save summary to database so it persists across requests
                MessageDAO.create(context.getDatasourceManager(), dbConnection, summaryMessage);
                logger.info("Saved summary message to database (id: " + summaryMessage.getId() + ")");

                // Build compacted message list: [summary] + [recent messages]
                llmMessages = new ArrayList<>();
                llmMessages.add(summaryMessage);

                // Add recent messages
                llmMessages.addAll(recentMessages);

                // Recalculate tokens after compaction
                // Sum actual tokens from recent messages + estimate for summary
                messageTokens = TokenCounter.estimateTokens(summaryMessage);
                for (Message msg : recentMessages) {
                    if (msg.getInputTokens() != null) messageTokens += msg.getInputTokens();
                    if (msg.getOutputTokens() != null) messageTokens += msg.getOutputTokens();
                }
                actualTotalTokens = systemPromptTokens + messageTokens;

                // Calculate reduction (old actual tokens vs summary estimate)
                int oldTokens = 0;
                for (Message msg : oldMessages) {
                    if (msg.getInputTokens() != null) oldTokens += msg.getInputTokens();
                    if (msg.getOutputTokens() != null) oldTokens += msg.getOutputTokens();
                }
                int reduction = oldTokens - TokenCounter.estimateTokens(summaryMessage);

                logger.info("Compaction complete - New token count: " + actualTotalTokens + " (reduced by " + reduction + " tokens)");

            } catch (Exception e) {
                logger.error("Error during compaction, continuing without compaction", e);
                // Continue with original uncompacted messages
            }
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
     * Generate a summary of old conversation messages using Claude API.
     * Used for conversation compaction to reduce token usage.
     *
     * @param claudeClient Claude API client
     * @param settings Module settings
     * @param messages Messages to summarize
     * @return Concise summary text
     * @throws Exception if summarization fails
     */
    private static String generateConversationSummary(ClaudeAPIClient claudeClient, IAISettings settings, List<Message> messages) throws Exception {
        logger.info("Generating summary for " + messages.size() + " messages");

        // Build conversation text for summarization
        StringBuilder conversationText = new StringBuilder();
        for (Message msg : messages) {
            conversationText.append(msg.getRole().toUpperCase()).append(": ");
            conversationText.append(msg.getContent());
            conversationText.append("\n\n");
        }

        // Build summarization request
        String summaryPrompt = "Summarize the following conversation concisely. " +
                              "Focus on key decisions, findings, and context that would be important for continuing the conversation. " +
                              "Use bullet points. Keep it under 500 words.\n\n" +
                              conversationText.toString();

        Message summaryRequestMsg = new Message();
        summaryRequestMsg.setRole("user");
        summaryRequestMsg.setContent(summaryPrompt);

        List<Message> summaryMessages = new ArrayList<>();
        summaryMessages.add(summaryRequestMsg);

        LLMRequest summaryRequest = new LLMRequest();
        summaryRequest.setModelName(settings.getModelName());
        summaryRequest.setMaxTokens(2000);
        summaryRequest.setSystemPrompt("You are a helpful assistant that summarizes technical conversations concisely.");
        summaryRequest.setMessages(summaryMessages);
        // No tools for summarization

        // Call Claude API
        LLMResponse summaryResponse = claudeClient.sendMessage(summaryRequest);

        logger.info("Summary generated successfully (" + summaryResponse.getOutputTokens() + " tokens)");

        return summaryResponse.getContent();
    }

    /**
     * Build system prompt with context variables injected.
     */
    /**
     * Get the default system prompt.
     */
    // ========== MODULAR SYSTEM PROMPT CONSTRUCTION ==========

    /**
     * Build system prompt using modular sections based on settings and context.
     * Pattern from modular-prompt-construction.md
     */
    private static String buildSystemPrompt(IAISettings settings, Conversation conversation, ToolRegistry toolRegistry) {
        // Use custom prompt from settings if provided
        String customPrompt = settings.getSystemPrompt();
        if (customPrompt != null && !customPrompt.trim().isEmpty()) {
            // Custom prompt - just inject basic variables and return
            return customPrompt
                .replace("{PROJECT_NAME}", conversation.getProjectName() != null ? conversation.getProjectName() : "Unknown")
                .replace("{USER_NAME}", conversation.getUserName() != null ? conversation.getUserName() : "Anonymous");
        }

        // Build modular prompt from sections
        StringBuilder prompt = new StringBuilder();

        // Identity (always included)
        prompt.append(sectionIdentity()).append("\n\n");

        // Role (conditional on execution mode)
        String roleSection = sectionRole(settings);
        if (!roleSection.isEmpty()) {
            prompt.append(roleSection).append("\n\n");
        }

        // Verification (always included - addresses hallucination issues)
        prompt.append(sectionVerification()).append("\n\n");

        // Tool Discovery (conditional on system function execution enabled)
        String toolDiscoverySection = sectionToolDiscovery(settings);
        if (!toolDiscoverySection.isEmpty()) {
            prompt.append(toolDiscoverySection).append("\n\n");
        }

        // Tool Usage (conditional on system function execution enabled)
        String toolUsageSection = sectionToolUsage(settings);
        if (!toolUsageSection.isEmpty()) {
            prompt.append(toolUsageSection).append("\n\n");
        }

        // Parameter Guidance (conditional on UNRESTRICTED mode)
        String parameterSection = sectionParameterGuidance(settings);
        if (!parameterSection.isEmpty()) {
            prompt.append(parameterSection).append("\n\n");
        }

        // Error Handling (always included)
        prompt.append(sectionErrorHandling()).append("\n\n");

        // Available Tools (always included)
        prompt.append(sectionAvailableTools(toolRegistry)).append("\n\n");

        // Response Style (always included)
        prompt.append(sectionResponseStyle());

        // Inject runtime context
        String finalPrompt = prompt.toString();
        finalPrompt = finalPrompt.replace("{PROJECT_NAME}", conversation.getProjectName() != null ? conversation.getProjectName() : "Unknown");
        finalPrompt = finalPrompt.replace("{USER_NAME}", conversation.getUserName() != null ? conversation.getUserName() : "Anonymous");

        return finalPrompt;
    }

    private static String sectionIdentity() {
        return "You are Ignition AI, an AI assistant integrated into Inductive Automation's Ignition SCADA platform.";
    }

    private static String sectionRole(IAISettings settings) {
        StringBuilder section = new StringBuilder("## Your Role\n");

        if (settings.getAllowSystemFunctionExecution()) {
            String mode = settings.getSystemFunctionMode();
            if ("UNRESTRICTED".equals(mode)) {
                section.append("You help users understand, explore, AND MODIFY Ignition systems. ");
                section.append("You can execute ANY system.* function including WRITE OPERATIONS ");
                section.append("(tag writes, database updates, alarm actions, configuration changes, etc.).");
            } else if ("READ_ONLY".equals(mode)) {
                section.append("You help users understand and explore Ignition systems. ");
                section.append("You can execute whitelisted READ-ONLY system.* functions ");
                section.append("(tag reads, database queries, alarm queries, etc.). Write operations are blocked.");
            } else {
                section.append("You help users understand and explore Ignition systems. ");
                section.append("You provide read-only analysis and cannot modify configurations, tags, or code.");
            }
        } else {
            section.append("You help users understand and explore Ignition systems. ");
            section.append("You provide read-only analysis and cannot modify configurations, tags, or code.");
        }

        return section.toString();
    }

    private static String sectionVerification() {
        return "## Verification Protocol\n" +
            "**CRITICAL:** Always verify operations with tools. Never guess or assume results.\n" +
            "- After writing data: Read it back to confirm the write succeeded\n" +
            "- After configuration changes: Query the configuration to verify\n" +
            "- When uncertain: Use tools to check actual state, never fabricate responses\n" +
            "- Show all tool executions in your responses so users can see what you checked";
    }

    private static String sectionToolDiscovery(IAISettings settings) {
        if (!settings.getAllowSystemFunctionExecution()) {
            return "";
        }

        String mode = settings.getSystemFunctionMode();
        if ("DISABLED".equals(mode)) {
            return "";
        }

        return "## Tool Discovery\n" +
            "Before saying you cannot do something, search for available functions:\n" +
            "- Use `list_system_functions` with a search query (e.g., query=\"tag rename\")\n" +
            "- There are 256+ system.* functions - always search rather than assuming\n" +
            "- Functions are organized by category: system.tag, system.db, system.alarm, system.util, etc.\n" +
            "- Use the category parameter to filter (e.g., category=\"system.tag\")";
    }

    private static String sectionToolUsage(IAISettings settings) {
        if (!settings.getAllowSystemFunctionExecution()) {
            return "";
        }

        String mode = settings.getSystemFunctionMode();
        if ("DISABLED".equals(mode)) {
            return "";
        }

        return "## Tool Usage Best Practices\n" +
            "When using `execute_system_function`:\n" +
            "- First use `list_system_functions` to find the right function and see its parameters\n" +
            "- Structure parameters as JSON objects: {\"paramName\": value}\n" +
            "- Use proper data types: lists for arrays, strings in quotes, numbers without quotes\n" +
            "- Always provide the project_name parameter (use {PROJECT_NAME} as default)\n" +
            "- Check the result for errors: look for \"error\" field in response\n" +
            "- If a function fails, try alternative functions or search for other approaches";
    }

    private static String sectionParameterGuidance(IAISettings settings) {
        if (!settings.getAllowSystemFunctionExecution()) {
            return "";
        }

        String mode = settings.getSystemFunctionMode();
        if (!"UNRESTRICTED".equals(mode)) {
            return "";
        }

        return "## Parameter Structure Examples\n" +
            "**Tag write:**\n" +
            "```json\n" +
            "{\"tagPaths\": [\"[default]MyTag\"], \"values\": [100]}\n" +
            "```\n\n" +
            "**Tag configure:**\n" +
            "```json\n" +
            "{\"basePath\": \"[default]\", \"tags\": [{\"name\": \"NewTag\", \"dataType\": \"Int4\", \"value\": 0}]}\n" +
            "```\n\n" +
            "**Database query:**\n" +
            "```json\n" +
            "{\"query\": \"SELECT * FROM table WHERE id = ?\", \"params\": [123], \"database\": \"MyDB\"}\n" +
            "```";
    }

    private static String sectionErrorHandling() {
        return "## Error Handling\n" +
            "When operations fail:\n" +
            "- Read the error message carefully - it often explains what went wrong\n" +
            "- Try alternative approaches (e.g., if configure fails, try a different collision policy)\n" +
            "- Search for other functions that might accomplish the same goal\n" +
            "- Verify your parameters match the expected structure\n" +
            "- If a function doesn't exist, search for similar functions";
    }

    private static String sectionAvailableTools(ToolRegistry toolRegistry) {
        StringBuilder section = new StringBuilder("## Available Tools\n");
        section.append("You have access to these tools:\n");

        for (String toolName : toolRegistry.getAllTools().keySet()) {
            section.append("- ").append(toolName).append("\n");
        }

        section.append("\nUse tools to retrieve actual system data. When users ask about system information, ");
        section.append("always use tools to get current, accurate data rather than making assumptions.");

        return section.toString();
    }

    private static String sectionResponseStyle() {
        return "## Response Style\n" +
            "- Be clear, concise, and technical\n" +
            "- Cite specific file paths and line numbers when referencing code\n" +
            "- Show your tool executions so users can see what you checked\n" +
            "- When you make changes, verify them and report the verified results";
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
