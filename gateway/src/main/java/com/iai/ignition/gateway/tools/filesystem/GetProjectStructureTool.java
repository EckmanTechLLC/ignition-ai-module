package com.iai.ignition.gateway.tools.filesystem;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.io.File;

/**
 * Tool for getting the project directory structure.
 * Returns a tree view of the project's file system.
 */
public class GetProjectStructureTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.filesystem.GetProjectStructureTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public GetProjectStructureTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "get_project_structure";
    }

    @Override
    public String getDescription() {
        return "Get the directory structure of an Ignition project. Returns a tree view of folders and files " +
                "within the project. Use this to understand the project's organization before reading specific files.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        // project_name parameter
        JsonObject projectName = new JsonObject();
        projectName.addProperty("type", "string");
        projectName.addProperty("description", "The name of the Ignition project");
        properties.add("project_name", projectName);

        // max_depth parameter (optional)
        JsonObject maxDepth = new JsonObject();
        maxDepth.addProperty("type", "integer");
        maxDepth.addProperty("description", "Maximum depth to traverse (default: 3)");
        maxDepth.addProperty("default", 3);
        properties.add("max_depth", maxDepth);

        schema.add("properties", properties);

        // Required parameters
        JsonArray required = new JsonArray();
        required.add("project_name");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        // Extract parameters
        String projectName = params.get("project_name").getAsString();
        int maxDepth = params.has("max_depth") ? params.get("max_depth").getAsInt() : 3;

        logger.debug("Getting project structure for: " + projectName + " (max_depth: " + maxDepth + ")");

        // Get gateway data path
        String gatewayDataPath = settings.getGatewayDataPath();
        if (gatewayDataPath == null || gatewayDataPath.isEmpty()) {
            throw new IllegalStateException("Gateway data path is not configured");
        }

        // Build project path
        File projectDir = new File(gatewayDataPath, "projects/" + projectName);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            throw new IllegalArgumentException("Project not found: " + projectName);
        }

        // Build directory tree
        JsonObject result = new JsonObject();
        result.addProperty("project_name", projectName);
        result.addProperty("project_path", projectDir.getAbsolutePath());

        JsonObject tree = buildDirectoryTree(projectDir, 0, maxDepth);
        result.add("structure", tree);

        return result;
    }

    /**
     * Recursively build a directory tree.
     */
    private JsonObject buildDirectoryTree(File dir, int currentDepth, int maxDepth) {
        JsonObject node = new JsonObject();
        node.addProperty("name", dir.getName());
        node.addProperty("type", "directory");

        if (currentDepth >= maxDepth) {
            node.addProperty("truncated", true);
            return node;
        }

        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return node;
        }

        JsonArray children = new JsonArray();
        for (File file : files) {
            if (file.isDirectory()) {
                children.add(buildDirectoryTree(file, currentDepth + 1, maxDepth));
            } else {
                JsonObject fileNode = new JsonObject();
                fileNode.addProperty("name", file.getName());
                fileNode.addProperty("type", "file");
                fileNode.addProperty("size", file.length());
                children.add(fileNode);
            }
        }

        node.add("children", children);
        return node;
    }
}
