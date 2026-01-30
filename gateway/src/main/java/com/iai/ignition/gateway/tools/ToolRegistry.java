package com.iai.ignition.gateway.tools;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
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
        logger.info("Discovering and registering IAI tools...");

        // Register File System Tools (11 tools)
        registerTool(new com.iai.ignition.gateway.tools.filesystem.GetProjectStructureTool(gatewayContext, settings));
        registerTool(new com.iai.ignition.gateway.tools.filesystem.GetFileMetadataTool(gatewayContext, settings));
        registerTool(new com.iai.ignition.gateway.tools.filesystem.ReadFileContentTool(gatewayContext, settings));
        registerTool(new com.iai.ignition.gateway.tools.filesystem.ListPerspectiveViewsTool(gatewayContext, settings));
        registerTool(new com.iai.ignition.gateway.tools.filesystem.ReadPerspectiveViewTool(gatewayContext, settings));
        registerTool(new com.iai.ignition.gateway.tools.filesystem.ListScriptModulesTool(gatewayContext, settings));
        registerTool(new com.iai.ignition.gateway.tools.filesystem.ReadScriptModuleTool(gatewayContext, settings));
        registerTool(new com.iai.ignition.gateway.tools.filesystem.ListNamedQueriesTool(gatewayContext, settings));
        registerTool(new com.iai.ignition.gateway.tools.filesystem.ReadNamedQueryTool(gatewayContext, settings));
        registerTool(new com.iai.ignition.gateway.tools.filesystem.SearchProjectFilesTool(gatewayContext, settings));
        registerTool(new com.iai.ignition.gateway.tools.filesystem.FindResourceByNameTool(gatewayContext, settings));

        // Register Tag Tools (3 tools)
        registerTool(new com.iai.ignition.gateway.tools.tags.ListTagsTool(gatewayContext, settings));
        registerTool(new com.iai.ignition.gateway.tools.tags.GetTagConfigTool(gatewayContext, settings));
        registerTool(new com.iai.ignition.gateway.tools.tags.QueryTagHistoryTool(gatewayContext, settings));

        // Register Alarm Tools (2 tools)
        registerTool(new com.iai.ignition.gateway.tools.alarms.QueryAlarmHistoryTool(gatewayContext, settings));
        registerTool(new com.iai.ignition.gateway.tools.alarms.GetAlarmConfigTool(gatewayContext, settings));

        // Database tools are gated by enableDatabaseTools setting (6 tools)
        if (settings.getEnableDatabaseTools()) {
            logger.info("Database tools are enabled.");
            registerTool(new com.iai.ignition.gateway.tools.database.ListDatabasesTool(gatewayContext, settings));
            registerTool(new com.iai.ignition.gateway.tools.database.ListTablesTool(gatewayContext, settings));
            registerTool(new com.iai.ignition.gateway.tools.database.DescribeTableTool(gatewayContext, settings));
            registerTool(new com.iai.ignition.gateway.tools.database.QueryTableTool(gatewayContext, settings));
            registerTool(new com.iai.ignition.gateway.tools.database.ExecuteNamedQueryTool(gatewayContext, settings));
            registerTool(new com.iai.ignition.gateway.tools.database.ExecuteSqlQueryTool(gatewayContext, settings));
        } else {
            logger.info("Database tools are disabled.");
        }

        // Register Search Tools (1 tool)
        registerTool(new com.iai.ignition.gateway.tools.search.SearchProjectResourcesTool(gatewayContext, settings));

        logger.info("Tool registration complete. Registered " + tools.size() + " tools.");
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
        logger.debug("Registered tool: " + name);
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
     * @return The result as a JSON object
     * @throws Exception if tool not found or execution fails
     */
    public JsonObject executeTool(String toolName, JsonObject params) throws Exception {
        IAITool tool = getTool(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Tool not found: " + toolName);
        }

        logger.debug("Executing tool: " + toolName);
        try {
            JsonObject result = tool.execute(params);
            logger.debug("Tool execution completed: " + toolName);
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
