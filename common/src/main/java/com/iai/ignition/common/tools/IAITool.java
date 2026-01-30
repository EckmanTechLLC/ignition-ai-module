package com.iai.ignition.common.tools;

import com.inductiveautomation.ignition.common.gson.JsonObject;

/**
 * Interface for Ignition AI tools.
 * Each tool provides a specific capability that Claude can invoke.
 */
public interface IAITool {

    /**
     * Get the unique name of this tool.
     * Used by Claude to invoke the tool.
     *
     * @return Tool name (e.g., "read_file_content")
     */
    String getName();

    /**
     * Get a description of what this tool does.
     * This description is sent to Claude to help it understand when to use the tool.
     *
     * @return Tool description
     */
    String getDescription();

    /**
     * Get the JSON Schema for the tool's parameters.
     * This schema defines what parameters the tool accepts.
     *
     * @return JSON Schema object
     */
    JsonObject getParameterSchema();

    /**
     * Execute the tool with the given parameters.
     *
     * @param params The parameters as a JSON object
     * @return The result as a JSON object
     * @throws Exception if execution fails
     */
    JsonObject execute(JsonObject params) throws Exception;
}
