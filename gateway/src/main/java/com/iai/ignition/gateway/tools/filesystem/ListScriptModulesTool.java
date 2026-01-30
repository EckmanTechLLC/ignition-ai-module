package com.iai.ignition.gateway.tools.filesystem;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Tool for listing Python script modules in a project.
 */
public class ListScriptModulesTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.filesystem.ListScriptModulesTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public ListScriptModulesTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "list_script_modules";
    }

    @Override
    public String getDescription() {
        return "List Python script modules in an Ignition project. Returns .py file paths under ignition/script-python.";
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

        JsonObject packagePath = new JsonObject();
        packagePath.addProperty("type", "string");
        packagePath.addProperty("description", "Optional package path (default: root)");
        packagePath.addProperty("default", "");
        properties.add("package", packagePath);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("project_name");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String projectName = params.get("project_name").getAsString();
        String packagePath = params.has("package") ? params.get("package").getAsString() : "";

        logger.debug("Listing script modules for: " + projectName + " (package: " + packagePath + ")");

        String gatewayDataPath = settings.getGatewayDataPath();
        if (gatewayDataPath == null || gatewayDataPath.isEmpty()) {
            throw new IllegalStateException("Gateway data path is not configured");
        }

        File scriptsDir = new File(gatewayDataPath, "projects/" + projectName + "/ignition/script-python/" + packagePath);
        if (!scriptsDir.exists() || !scriptsDir.isDirectory()) {
            throw new IllegalArgumentException("Scripts directory not found: " + packagePath);
        }

        List<String> scripts = new ArrayList<>();
        collectScripts(scriptsDir, packagePath, scripts);

        JsonObject result = new JsonObject();
        result.addProperty("project_name", projectName);
        result.addProperty("package", packagePath);

        JsonArray scriptsArray = new JsonArray();
        for (String script : scripts) {
            scriptsArray.add(script);
        }
        result.add("scripts", scriptsArray);
        result.addProperty("count", scripts.size());

        return result;
    }

    private void collectScripts(File dir, String basePath, List<String> scripts) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                String subPath = basePath.isEmpty() ? file.getName() : basePath + "/" + file.getName();
                collectScripts(file, subPath, scripts);
            } else if (file.getName().endsWith(".py")) {
                String scriptPath = basePath.isEmpty() ? file.getName() : basePath + "/" + file.getName();
                scripts.add(scriptPath);
            }
        }
    }
}
