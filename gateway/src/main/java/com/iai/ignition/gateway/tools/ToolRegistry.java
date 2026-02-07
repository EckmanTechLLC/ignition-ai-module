package com.iai.ignition.gateway.tools;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.iai.ignition.gateway.tools.scripting.ScriptExecutor;
import com.iai.ignition.gateway.tools.scripting.ListSystemFunctionsTool;
import com.iai.ignition.gateway.tools.scripting.ExecuteSystemFunctionTool;
import com.iai.ignition.gateway.tools.tasks.CreateScheduledTaskTool;
import com.iai.ignition.gateway.tools.tasks.ListScheduledTasksTool;
import com.iai.ignition.gateway.tools.tasks.ManageScheduledTaskTool;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.util.*;

/**
 * Registry for all available IAI tools.
 * Manages tool discovery, registration, and execution.
 */
public class ToolRegistry {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.ToolRegistry");

    private final Map<String, IAITool> tools = new HashMap<>();
    private final GatewayContext gatewayContext;
    private final IAISettings settings;
    private ScriptExecutor scriptExecutor;

    /**
     * Create a tool registry.
     *
     * @param gatewayContext The gateway context
     * @param settings The module settings
     */
    public ToolRegistry(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
        discoverAndRegisterTools();
    }

    /**
     * Discover and register all available tools.
     * Tools are conditionally registered based on settings.
     */
    private void discoverAndRegisterTools() {
        // Core Meta-Tools (3 tools)
        registerTool(new com.iai.ignition.gateway.tools.conversation.QueryConversationMemoryTool(gatewayContext, settings));
        registerTool(new com.iai.ignition.gateway.tools.filesystem.ProjectFilesTool(gatewayContext, settings));
        registerTool(new com.iai.ignition.gateway.tools.filesystem.SearchResourcesTool(gatewayContext, settings));

        // Database tools are gated by enableDatabaseTools setting (6 tools)
        if (settings.getEnableDatabaseTools()) {
            registerTool(new com.iai.ignition.gateway.tools.database.ListDatabasesTool(gatewayContext, settings));
            registerTool(new com.iai.ignition.gateway.tools.database.ListTablesTool(gatewayContext, settings));
            registerTool(new com.iai.ignition.gateway.tools.database.DescribeTableTool(gatewayContext, settings));
            registerTool(new com.iai.ignition.gateway.tools.database.QueryTableTool(gatewayContext, settings));
            registerTool(new com.iai.ignition.gateway.tools.database.ExecuteNamedQueryTool(gatewayContext, settings));
            registerTool(new com.iai.ignition.gateway.tools.database.ExecuteSqlQueryTool(gatewayContext, settings));
        }

        // System function execution tools (gated by AllowSystemFunctionExecution) (2 tools)
        if (settings.getAllowSystemFunctionExecution()) {
            scriptExecutor = new ScriptExecutor(gatewayContext, settings);
            registerTool(new ListSystemFunctionsTool(gatewayContext, settings, scriptExecutor));
            registerTool(new ExecuteSystemFunctionTool(gatewayContext, settings, scriptExecutor));
        }

        // Scheduled task management tools (3 tools - always available)
        registerTool(new CreateScheduledTaskTool(gatewayContext, settings));
        registerTool(new ListScheduledTasksTool(gatewayContext, settings));
        registerTool(new ManageScheduledTaskTool(gatewayContext, settings));

        logger.info("IAI tools registered: " + tools.size() + " available");
    }

    /**
     * Register a tool in the registry.
     *
     * @param tool The tool to register
     */
    public void registerTool(IAITool tool) {
        if (tool == null) {
            logger.warn("Attempted to register null tool");
            return;
        }

        String name = tool.getName();
        if (name == null || name.isEmpty()) {
            logger.warn("Attempted to register tool with null or empty name");
            return;
        }

        if (tools.containsKey(name)) {
            logger.warn("Tool already registered: " + name + ". Replacing.");
        }

        tools.put(name, tool);
    }

    /**
     * Get a tool by name.
     *
     * @param name The tool name
     * @return The tool, or null if not found
     */
    public IAITool getTool(String name) {
        return tools.get(name);
    }

    /**
     * Get all registered tools.
     *
     * @return Unmodifiable map of tool name to tool
     */
    public Map<String, IAITool> getAllTools() {
        return Collections.unmodifiableMap(tools);
    }

    /**
     * Get tool definitions for Claude API.
     * Converts tools to the format expected by Claude.
     *
     * @return List of tool definitions with name, description, and input_schema
     */
    public List<Map<String, Object>> getToolDefinitions() {
        List<Map<String, Object>> definitions = new ArrayList<>();

        for (IAITool tool : tools.values()) {
            Map<String, Object> definition = new HashMap<>();
            definition.put("name", tool.getName());
            definition.put("description", tool.getDescription());
            definition.put("input_schema", tool.getParameterSchema());
            definitions.add(definition);
        }

        return definitions;
    }

    /**
     * Execute a tool by name with the given parameters.
     *
     * @param toolName The name of the tool to execute
     * @param params The parameters as a JSON object
     * @param conversationId The current conversation ID (for context-aware tools)
     * @param userName The current user name from conversation context
     * @param projectName The current project name from conversation context
     * @return The result as a JSON object
     * @throws Exception if tool not found or execution fails
     */
    public JsonObject executeTool(String toolName, JsonObject params, String conversationId, String userName, String projectName) throws Exception {
        IAITool tool = getTool(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Tool not found: " + toolName);
        }

        // Set conversation ID for memory tool
        if (tool instanceof com.iai.ignition.gateway.tools.conversation.QueryConversationMemoryTool) {
            ((com.iai.ignition.gateway.tools.conversation.QueryConversationMemoryTool) tool)
                .setConversationId(conversationId);
        }

        // Set context for task management tools
        if (tool instanceof com.iai.ignition.gateway.tools.tasks.CreateScheduledTaskTool) {
            ((com.iai.ignition.gateway.tools.tasks.CreateScheduledTaskTool) tool)
                .setContext(userName, projectName);
        }
        if (tool instanceof com.iai.ignition.gateway.tools.tasks.ListScheduledTasksTool) {
            ((com.iai.ignition.gateway.tools.tasks.ListScheduledTasksTool) tool)
                .setContext(userName, projectName);
        }

        try {
            JsonObject result = tool.execute(params);
            return result;
        } catch (Exception e) {
            logger.error("Error executing tool: " + toolName, e);
            throw e;
        }
    }

    /**
     * Check if a tool is registered.
     *
     * @param toolName The tool name
     * @return true if the tool is registered
     */
    public boolean hasTool(String toolName) {
        return tools.containsKey(toolName);
    }

    /**
     * Get the number of registered tools.
     *
     * @return Tool count
     */
    public int getToolCount() {
        return tools.size();
    }
}
