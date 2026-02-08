package com.iai.ignition.gateway.endpoints;

import com.iai.ignition.common.llm.LLMRequest;
import com.iai.ignition.common.llm.LLMResponse;
import com.iai.ignition.common.model.Conversation;
import com.iai.ignition.common.model.DebugLog;
import com.iai.ignition.common.model.Message;
import com.iai.ignition.common.model.ScheduledTask;
import com.iai.ignition.common.model.TaskExecution;
import com.iai.ignition.common.model.ToolCall;
import com.iai.ignition.common.model.ToolResult;
import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.database.ConversationDAO;
import com.iai.ignition.gateway.database.DebugLogDAO;
import com.iai.ignition.gateway.database.MessageDAO;
import com.iai.ignition.gateway.database.TaskDAO;
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
import java.text.ParseException;
import java.util.Calendar;
import java.util.*;

/**
 * RPC endpoints for conversation management and AI interaction.
 * Exposed to Perspective components via REST API.
 */
public final class ConversationEndpoints {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.endpoints.ConversationEndpoints");
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
        // Test endpoint to verify routing works
        routes.newRoute("/test")
            .type(RouteGroup.TYPE_JSON)
            .handler((req, res) -> {
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "Routing is working!");
                return response;
            })
            .mount();

        // POST /sendMessage - Send a message and get AI response
        routes.newRoute("/sendMessage")
            .method(HttpMethod.POST)
            .type(RouteGroup.TYPE_JSON)
            .handler(ConversationEndpoints::sendMessage)
            .mount();

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

        // POST /createTask - Create a scheduled task
        routes.newRoute("/createTask")
            .method(HttpMethod.POST)
            .type(RouteGroup.TYPE_JSON)
            .handler(ConversationEndpoints::createTask)
            .mount();

        // GET /listTasks - List tasks for user/project
        routes.newRoute("/listTasks")
            .type(RouteGroup.TYPE_JSON)
            .handler(ConversationEndpoints::listTasks)
            .mount();

        // POST /pauseTask/:id - Pause a task
        routes.newRoute("/pauseTask/:id")
            .method(HttpMethod.POST)
            .type(RouteGroup.TYPE_JSON)
            .handler((req, res) -> pauseTask(req, res, req.getParameter("id")))
            .mount();

        // POST /resumeTask/:id - Resume a task
        routes.newRoute("/resumeTask/:id")
            .method(HttpMethod.POST)
            .type(RouteGroup.TYPE_JSON)
            .handler((req, res) -> resumeTask(req, res, req.getParameter("id")))
            .mount();

        // DELETE /deleteTask/:id - Delete a task
        routes.newRoute("/deleteTask/:id")
            .method(HttpMethod.DELETE)
            .type(RouteGroup.TYPE_JSON)
            .handler((req, res) -> deleteTask(req, res, req.getParameter("id")))
            .mount();

        // GET /getTaskExecutions/:id - Get execution history
        routes.newRoute("/getTaskExecutions/:id")
            .type(RouteGroup.TYPE_JSON)
            .handler((req, res) -> getTaskExecutions(req, res, req.getParameter("id")))
            .mount();
    }

    /**
     * Send a message and get AI response.
     * This is the main endpoint for chat interaction.
     */
    private static JsonObject sendMessage(RequestContext req, HttpServletResponse res) {
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
                logger.debug("Created new conversation: " + conversation.getId());
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

            // Include tool results if any
            if (assistantMessage.getToolResults() != null && !assistantMessage.getToolResults().isEmpty()) {
                JsonArray toolResultsArray = new JsonArray();
                for (ToolResult tr : assistantMessage.getToolResults()) {
                    JsonObject toolResultObj = new JsonObject();
                    toolResultObj.addProperty("toolCallId", tr.getToolCallId());
                    toolResultObj.addProperty("content", tr.getContent());
                    toolResultObj.addProperty("isError", tr.isError());
                    toolResultsArray.add(toolResultObj);
                }
                response.add("toolResults", toolResultsArray);
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
                logger.debug("Found existing summary at message " + i);
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
            logger.debug("Using compacted history");
        } else if (latestSummary != null && summaryIndex == allMessages.size() - 1) {
            // Summary is the last message (edge case)
            history = new ArrayList<>();
            history.add(latestSummary);
            logger.debug("Using only summary");
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


        // Automatic compaction if approaching context limit
        int contextLimit = 200000; // Claude context limit

        if (enableAutoCompaction && actualTotalTokens > compactionTokenThreshold && llmMessages.size() > compactToRecentMessages) {
            logger.info("Auto-compaction triggered: " + actualTotalTokens + " tokens");

            try {
                // Split messages: old (to summarize) vs recent (keep full)
                List<Message> oldMessages = llmMessages.subList(0, llmMessages.size() - compactToRecentMessages);
                List<Message> recentMessages = llmMessages.subList(llmMessages.size() - compactToRecentMessages, llmMessages.size());


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
                logger.debug("Saved summary message");

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

                logger.info("Compaction complete: reduced by " + reduction + " tokens");

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

        // Generate assistant message ID upfront for debug log linking
        String assistantMessageId = UUID.randomUUID().toString();

        // Collect debug logs during loop, save after message is created
        List<DebugLog> debugLogs = new ArrayList<>();

        int maxToolIterations = settings.getMaxToolIterations() != null ? settings.getMaxToolIterations() : 10;
        while (iteration < maxToolIterations) {
            iteration++;

            // Build request
            LLMRequest request = new LLMRequest();
            request.setModelName(settings.getModelName());
            request.setMaxTokens(4096);
            request.setSystemPrompt(systemPrompt);
            request.setMessages(llmMessages);
            request.setTools(toolDefinitions);

            // Serialize request for debug logging
            String requestJson = gson.toJson(request);

            // Call Claude API
            llmResponse = claudeClient.sendMessage(request);

            // Serialize response for debug logging
            String responseJson = gson.toJson(llmResponse);

            // Collect debug log for this iteration (save later after message exists)
            DebugLog debugLog = new DebugLog();
            debugLog.setId(UUID.randomUUID().toString());
            debugLog.setMessageId(assistantMessageId);
            debugLog.setRequestJson(requestJson);
            debugLog.setResponseJson(responseJson);
            debugLog.setTimestamp(System.currentTimeMillis());
            debugLogs.add(debugLog);

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
                    JsonObject toolResult = toolRegistry.executeTool(
                        toolCall.getName(),
                        inputParams,
                        conversation.getId(),
                        conversation.getUserName(),
                        conversation.getProjectName()
                    );

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

        if (iteration >= maxToolIterations) {
            logger.warn("Max tool iterations reached (" + maxToolIterations + "), returning partial response");
        }

        // Save assistant message
        Message assistantMessage = new Message();
        assistantMessage.setId(assistantMessageId); // Use pre-generated ID for debug log linking
        assistantMessage.setConversationId(conversation.getId());
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(llmResponse.getContent());
        // Save toolCalls and toolResults for audit/display purposes
        // (Note: toolResults are ALSO saved to separate user messages for Claude API, but we attach
        // them here so the HTTP response can include them for frontend display. They're stripped
        // before being sent to the API anyway - see line 370)
        assistantMessage.setToolCalls(allToolCalls.isEmpty() ? null : allToolCalls);
        assistantMessage.setToolResults(allToolResults.isEmpty() ? null : allToolResults);
        assistantMessage.setInputTokens(llmResponse.getInputTokens());
        assistantMessage.setOutputTokens(llmResponse.getOutputTokens());
        assistantMessage.setTimestamp(System.currentTimeMillis());

        MessageDAO.create(context.getDatasourceManager(), dbConnection, assistantMessage);

        // Now save debug logs (after message exists to satisfy foreign key constraint)
        for (DebugLog debugLog : debugLogs) {
            try {
                DebugLogDAO.create(context.getDatasourceManager(), dbConnection, debugLog);
            } catch (Exception e) {
                logger.error("Failed to save debug log", e);
                // Don't fail the conversation if debug logging fails
            }
        }
        logger.debug("Saved " + debugLogs.size() + " debug log(s) for message " + assistantMessageId);

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

        // Protocol (always included - core behavior)
        prompt.append(sectionVerification()).append("\n\n");

        // Role (conditional on execution mode)
        String roleSection = sectionRole(settings);
        if (!roleSection.isEmpty()) {
            prompt.append(roleSection).append("\n\n");
        }

        // Function Discovery (conditional on system function execution enabled)
        String toolDiscoverySection = sectionToolDiscovery(settings);
        if (!toolDiscoverySection.isEmpty()) {
            prompt.append(toolDiscoverySection).append("\n\n");
        }

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
        return "## Protocol (MANDATORY)\n" +
            "You MUST use tools for EVERY response. No exceptions.\n\n" +
            "- To recall previous conversation: use query_conversation_memory\n" +
            "- To interact with Ignition (read/write tags, execute commands): use execute_system_function\n" +
            "- To read project files: use project_files\n" +
            "- To search for resources: use search_resources\n\n" +
            "NEVER respond without using at least one tool.\n" +
            "NEVER claim to have done something without executing the actual tool.\n" +
            "If a tool returns an error, analyze the error, fix the issue, and retry.";
    }

    private static String sectionToolDiscovery(IAISettings settings) {
        if (!settings.getAllowSystemFunctionExecution()) {
            return "";
        }

        String mode = settings.getSystemFunctionMode();
        if ("DISABLED".equals(mode)) {
            return "";
        }

        return "## Function Discovery\n" +
            "Search for available functions using `list_system_functions` before saying you cannot do something. " +
            "There are 256+ system.* functions across multiple categories.";
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
            "Be clear and technical. Show your work by displaying tool executions in your responses.\n\n" +
            "## Data Visualization\n" +
            "When you query data (tag history, alarms, database tables), the UI automatically renders interactive charts and tables. " +
            "DO NOT create ASCII charts, text-based visualizations, or markdown tables in your response. " +
            "Simply describe what you found and what it means - the chart will appear automatically for the user. " +
            "Focus on insights, patterns, and recommendations rather than trying to visualize the data yourself.";
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

    /**
     * Create a scheduled task.
     */
    private static JsonObject createTask(RequestContext req, HttpServletResponse res) {
        JsonObject response = new JsonObject();
        GatewayContext context = req.getGatewayContext();

        try {
            String requestBodyString = req.readBody();
            JsonObject requestBody = new JsonParser().parse(requestBodyString).getAsJsonObject();

            String userName = requestBody.has("userName") ? requestBody.get("userName").getAsString() : null;
            String projectName = requestBody.get("projectName").getAsString();
            String taskDescription = requestBody.get("taskDescription").getAsString();
            String prompt = requestBody.get("prompt").getAsString();
            String cronExpression = requestBody.get("cronExpression").getAsString();
            String resultStorage = requestBody.get("resultStorage").getAsString();
            String conversationId = requestBody.has("conversationId") ? requestBody.get("conversationId").getAsString() : null;

            IAISettings settings = context.getLocalPersistenceInterface().find(IAISettings.META, 0L);
            if (settings == null) {
                throw new IllegalStateException("IAI settings not found");
            }

            String dbConnection = settings.getDatabaseConnection();

            // Calculate next run time
            long nextRunAt = calculateNextRunTime(cronExpression);

            // Create task
            ScheduledTask task = new ScheduledTask();
            task.setId(UUID.randomUUID().toString());
            task.setUserName(userName);
            task.setProjectName(projectName);
            task.setTaskDescription(taskDescription);
            task.setConversationId(conversationId);
            task.setPrompt(prompt);
            task.setCronExpression(cronExpression);
            task.setNextRunAt(nextRunAt);
            task.setStatus("PENDING");
            task.setResultStorage(resultStorage);
            task.setCreatedAt(System.currentTimeMillis());
            task.setEnabled(true);

            boolean created = TaskDAO.createTask(context.getDatasourceManager(), dbConnection, task);

            if (created) {
                response.addProperty("success", true);
                response.addProperty("taskId", task.getId());
                response.addProperty("nextRunAt", nextRunAt);
            } else {
                response.addProperty("success", false);
                response.addProperty("error", "Failed to create task");
                res.setStatus(500);
            }

        } catch (Exception e) {
            logger.error("Error creating task", e);
            response.addProperty("success", false);
            response.addProperty("error", e.getMessage());
            res.setStatus(500);
        }

        return response;
    }

    /**
     * List tasks for a user/project.
     */
    private static JsonObject listTasks(RequestContext req, HttpServletResponse res) {
        JsonObject response = new JsonObject();
        GatewayContext context = req.getGatewayContext();

        try {
            String userName = req.getParameter("userName");
            String projectName = req.getParameter("projectName");

            IAISettings settings = context.getLocalPersistenceInterface().find(IAISettings.META, 0L);
            if (settings == null) {
                throw new IllegalStateException("IAI settings not found");
            }

            String dbConnection = settings.getDatabaseConnection();

            List<ScheduledTask> tasks = TaskDAO.getTasksByUser(
                context.getDatasourceManager(),
                dbConnection,
                userName,
                projectName
            );

            JsonArray tasksArray = new JsonArray();
            for (ScheduledTask task : tasks) {
                JsonObject taskObj = new JsonObject();
                taskObj.addProperty("id", task.getId());
                taskObj.addProperty("taskDescription", task.getTaskDescription());
                taskObj.addProperty("prompt", task.getPrompt());
                taskObj.addProperty("cronExpression", task.getCronExpression());
                taskObj.addProperty("nextRunAt", task.getNextRunAt());
                if (task.getLastRunAt() != null) {
                    taskObj.addProperty("lastRunAt", task.getLastRunAt());
                }
                taskObj.addProperty("status", task.getStatus());
                taskObj.addProperty("enabled", task.isEnabled());
                taskObj.addProperty("resultStorage", task.getResultStorage());
                if (task.getConversationId() != null) {
                    taskObj.addProperty("conversationId", task.getConversationId());
                }
                tasksArray.add(taskObj);
            }

            response.addProperty("success", true);
            response.add("tasks", tasksArray);

        } catch (Exception e) {
            logger.error("Error listing tasks", e);
            response.addProperty("success", false);
            response.addProperty("error", e.getMessage());
            res.setStatus(500);
        }

        return response;
    }

    /**
     * Pause a task.
     */
    private static JsonObject pauseTask(RequestContext req, HttpServletResponse res, String taskId) {
        JsonObject response = new JsonObject();
        GatewayContext context = req.getGatewayContext();

        try {
            IAISettings settings = context.getLocalPersistenceInterface().find(IAISettings.META, 0L);
            if (settings == null) {
                throw new IllegalStateException("IAI settings not found");
            }

            String dbConnection = settings.getDatabaseConnection();

            boolean updated = TaskDAO.updateTaskEnabled(
                context.getDatasourceManager(),
                dbConnection,
                taskId,
                false
            );

            if (updated) {
                response.addProperty("success", true);
                response.addProperty("message", "Task paused");
            } else {
                response.addProperty("success", false);
                response.addProperty("error", "Task not found");
                res.setStatus(404);
            }

        } catch (Exception e) {
            logger.error("Error pausing task", e);
            response.addProperty("success", false);
            response.addProperty("error", e.getMessage());
            res.setStatus(500);
        }

        return response;
    }

    /**
     * Resume a task.
     */
    private static JsonObject resumeTask(RequestContext req, HttpServletResponse res, String taskId) {
        JsonObject response = new JsonObject();
        GatewayContext context = req.getGatewayContext();

        try {
            IAISettings settings = context.getLocalPersistenceInterface().find(IAISettings.META, 0L);
            if (settings == null) {
                throw new IllegalStateException("IAI settings not found");
            }

            String dbConnection = settings.getDatabaseConnection();

            boolean updated = TaskDAO.updateTaskEnabled(
                context.getDatasourceManager(),
                dbConnection,
                taskId,
                true
            );

            if (updated) {
                response.addProperty("success", true);
                response.addProperty("message", "Task resumed");
            } else {
                response.addProperty("success", false);
                response.addProperty("error", "Task not found");
                res.setStatus(404);
            }

        } catch (Exception e) {
            logger.error("Error resuming task", e);
            response.addProperty("success", false);
            response.addProperty("error", e.getMessage());
            res.setStatus(500);
        }

        return response;
    }

    /**
     * Delete a task.
     */
    private static JsonObject deleteTask(RequestContext req, HttpServletResponse res, String taskId) {
        JsonObject response = new JsonObject();
        GatewayContext context = req.getGatewayContext();

        try {
            IAISettings settings = context.getLocalPersistenceInterface().find(IAISettings.META, 0L);
            if (settings == null) {
                throw new IllegalStateException("IAI settings not found");
            }

            String dbConnection = settings.getDatabaseConnection();

            boolean deleted = TaskDAO.deleteTask(
                context.getDatasourceManager(),
                dbConnection,
                taskId
            );

            if (deleted) {
                response.addProperty("success", true);
                response.addProperty("message", "Task deleted");
            } else {
                response.addProperty("success", false);
                response.addProperty("error", "Task not found");
                res.setStatus(404);
            }

        } catch (Exception e) {
            logger.error("Error deleting task", e);
            response.addProperty("success", false);
            response.addProperty("error", e.getMessage());
            res.setStatus(500);
        }

        return response;
    }

    /**
     * Get execution history for a task.
     */
    private static JsonObject getTaskExecutions(RequestContext req, HttpServletResponse res, String taskId) {
        JsonObject response = new JsonObject();
        GatewayContext context = req.getGatewayContext();

        try {
            String limitStr = req.getParameter("limit");
            int limit = limitStr != null ? Integer.parseInt(limitStr) : 20;

            IAISettings settings = context.getLocalPersistenceInterface().find(IAISettings.META, 0L);
            if (settings == null) {
                throw new IllegalStateException("IAI settings not found");
            }

            String dbConnection = settings.getDatabaseConnection();

            List<TaskExecution> executions = TaskDAO.getTaskExecutions(
                context.getDatasourceManager(),
                dbConnection,
                taskId,
                limit
            );

            JsonArray executionsArray = new JsonArray();
            for (TaskExecution execution : executions) {
                JsonObject execObj = new JsonObject();
                execObj.addProperty("id", execution.getId());
                execObj.addProperty("executedAt", execution.getExecutedAt());
                execObj.addProperty("status", execution.getStatus());
                if (execution.getConversationId() != null) {
                    execObj.addProperty("conversationId", execution.getConversationId());
                }
                if (execution.getErrorMessage() != null) {
                    execObj.addProperty("errorMessage", execution.getErrorMessage());
                }
                if (execution.getExecutionTimeMs() != null) {
                    execObj.addProperty("executionTimeMs", execution.getExecutionTimeMs());
                }
                executionsArray.add(execObj);
            }

            response.addProperty("success", true);
            response.add("executions", executionsArray);

        } catch (Exception e) {
            logger.error("Error getting task executions", e);
            response.addProperty("success", false);
            response.addProperty("error", e.getMessage());
            res.setStatus(500);
        }

        return response;
    }

    /**
     * Calculate next run time based on cron expression.
     * Simple implementation for common patterns.
     */
    private static long calculateNextRunTime(String cronExpressionStr) throws ParseException {
        String[] parts = cronExpressionStr.trim().split("\\s+");
        if (parts.length != 5) {
            throw new ParseException("Invalid cron format, expected 5 fields", 0);
        }

        int minute = "*".equals(parts[0]) ? 0 : Integer.parseInt(parts[0]);
        int hour = "*".equals(parts[1]) ? 0 : Integer.parseInt(parts[1]);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.HOUR_OF_DAY, hour);

        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        return cal.getTimeInMillis();
    }
}
