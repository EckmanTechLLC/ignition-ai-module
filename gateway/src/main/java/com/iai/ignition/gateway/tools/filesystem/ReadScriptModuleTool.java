package com.iai.ignition.gateway.tools.filesystem;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.io.File;
import java.nio.file.Files;

/**
 * Tool for reading a Python script module.
 */
public class ReadScriptModuleTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.filesystem.ReadScriptModuleTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public ReadScriptModuleTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "read_script_module";
    }

    @Override
    public String getDescription() {
        return "Read a Python script module from an Ignition project. Returns the Python file content.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject projectName = new JsonObject();
        projectName.addProperty("type", "string");
        projectName.addProperty("description", "The name of the Ignition project");
        properties.add("project_name", projectName);

        JsonObject scriptPath = new JsonObject();
        scriptPath.addProperty("type", "string");
        scriptPath.addProperty("description", "Path to the script (e.g., 'utils.py' or 'package/module.py')");
        properties.add("script_path", scriptPath);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("project_name");
        required.add("script_path");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String projectName = params.get("project_name").getAsString();
        String scriptPath = params.get("script_path").getAsString();

        logger.debug("Reading script module: " + projectName + "/" + scriptPath);

        String gatewayDataPath = settings.getGatewayDataPath();
        if (gatewayDataPath == null || gatewayDataPath.isEmpty()) {
            throw new IllegalStateException("Gateway data path is not configured");
        }

        File scriptFile = new File(gatewayDataPath, "projects/" + projectName + "/ignition/script-python/" + scriptPath);
        if (!scriptFile.exists() || !scriptFile.isFile()) {
            throw new IllegalArgumentException("Script not found: " + scriptPath);
        }

        String content = new String(Files.readAllBytes(scriptFile.toPath()));

        JsonObject result = new JsonObject();
        result.addProperty("project_name", projectName);
        result.addProperty("script_path", scriptPath);
        result.addProperty("size", scriptFile.length());
        result.addProperty("content", content);

        return result;
    }
}
