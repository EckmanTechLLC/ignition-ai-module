package com.iai.ignition.gateway.tools.scripting;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.util.concurrent.TimeoutException;

/**
 * Tool for executing Ignition system.* scripting functions via Jython.
 * Provides 100% coverage of all system functions with configurable safety modes.
 */
public class ExecuteSystemFunctionTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.scripting.ExecuteSystemFunctionTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;
    private final ScriptExecutor scriptExecutor;

    public ExecuteSystemFunctionTool(GatewayContext gatewayContext, IAISettings settings, ScriptExecutor scriptExecutor) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
        this.scriptExecutor = scriptExecutor;
    }

    @Override
    public String getName() {
        return "execute_system_function";
    }

    @Override
    public String getDescription() {
        return "Execute an Ignition system.* scripting function by name with parameters. " +
               "Actual Jython script execution with 100% function coverage. " +
               "Supports positional arguments (use numeric keys: \"0\", \"1\", \"2\") and keyword arguments (use named keys). " +
               "Example: params={\"0\": \"*\", \"recursive\": true} calls function('*', recursive=true). " +
               "Safety mode determines which functions are available: " +
               "READ_ONLY (default, whitelisted safe functions only), " +
               "UNRESTRICTED (all functions, testing only), " +
               "DISABLED (no execution).";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        // Function name
        JsonObject functionName = new JsonObject();
        functionName.addProperty("type", "string");
        functionName.addProperty("description", "System function name (e.g., 'system.tag.read', 'system.db.runQuery')");
        properties.add("function_name", functionName);

        // Parameters
        JsonObject params = new JsonObject();
        params.addProperty("type", "object");
        params.addProperty("description", "Function-specific parameters. Use numeric keys (\"0\", \"1\", \"2\") for positional arguments, or named keys for keyword arguments. Example: {\"0\": \"*\", \"recursive\": true} calls function('*', recursive=true)");
        properties.add("params", params);

        // Project name
        JsonObject projectName = new JsonObject();
        projectName.addProperty("type", "string");
        projectName.addProperty("description", "Project name for script execution context");
        properties.add("project_name", projectName);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("function_name");
        required.add("params");
        required.add("project_name");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        // Check if execution is enabled
        if (!settings.getAllowSystemFunctionExecution()) {
            throw new IllegalStateException("System function execution is disabled. Enable in Gateway settings.");
        }

        // Check mode
        String mode = settings.getSystemFunctionMode();
        if (mode.equals("DISABLED")) {
            throw new SecurityException("System function execution mode is DISABLED");
        }

        // Extract parameters
        String functionName = params.get("function_name").getAsString();
        JsonObject functionParams = params.get("params").getAsJsonObject();
        String projectName = params.get("project_name").getAsString();

        logger.info("Executing system function: " + functionName + " in project: " + projectName);

        // Check whitelist for READ_ONLY mode
        if (mode.equals("READ_ONLY")) {
            if (!scriptExecutor.isReadOnlyWhitelisted(functionName)) {
                throw new SecurityException(
                    "Function '" + functionName + "' is not whitelisted for READ_ONLY mode. " +
                    "Switch to UNRESTRICTED mode to execute write operations (testing only)."
                );
            }
        }

        try {
            // Execute function via script executor
            long startTime = System.currentTimeMillis();
            JsonObject result = scriptExecutor.executeSystemFunction(
                functionName,
                functionParams,
                projectName
            );
            long duration = System.currentTimeMillis() - startTime;

            // Check if result contains error
            if (result.has("error")) {
                logger.warn("System function returned error: " + result.get("error").getAsString());
                // Don't throw - return error in result for AI to handle
            }

            // Enforce size limits
            int resultSizeKB = result.toString().length() / 1024;
            int maxSizeKB = settings.getMaxSystemFunctionResultSizeKB();

            if (resultSizeKB > maxSizeKB) {
                logger.warn("Result size (" + resultSizeKB + " KB) exceeds limit (" + maxSizeKB + " KB), truncating");
                // Truncate result
                JsonObject truncated = new JsonObject();
                truncated.addProperty("truncated", true);
                truncated.addProperty("originalSizeKB", resultSizeKB);
                truncated.addProperty("maxSizeKB", maxSizeKB);
                truncated.addProperty("message", "Result too large, showing partial data");
                // Include small portion of original result
                truncated.addProperty("partialResult", result.toString().substring(0, Math.min(1000, result.toString().length())));
                result = truncated;
            }

            // Add execution metadata
            result.addProperty("executionTimeMs", duration);
            result.addProperty("functionName", functionName);
            result.addProperty("mode", mode);

            logger.debug("System function executed in " + duration + "ms");

            return result;

        } catch (TimeoutException e) {
            logger.error("System function timeout: " + functionName, e);
            throw new Exception(
                "Function execution timeout after " +
                settings.getSystemFunctionTimeoutSeconds() + " seconds"
            );
        } catch (SecurityException e) {
            logger.warn("Security violation: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error executing system function: " + functionName, e);
            throw new Exception("Execution error: " + e.getMessage(), e);
        }
    }
}
