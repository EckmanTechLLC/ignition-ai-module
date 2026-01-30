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
 * Tool for finding resources by name across a project (fuzzy matching).
 */
public class FindResourceByNameTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.filesystem.FindResourceByNameTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public FindResourceByNameTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "find_resource_by_name";
    }

    @Override
    public String getDescription() {
        return "Find resources (views, scripts, queries) by name across the project. Uses fuzzy matching to find files whose names contain the search term.";
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

        JsonObject resourceName = new JsonObject();
        resourceName.addProperty("type", "string");
        resourceName.addProperty("description", "The resource name to search for (case-insensitive partial match)");
        properties.add("resource_name", resourceName);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("project_name");
        required.add("resource_name");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String projectName = params.get("project_name").getAsString();
        String resourceName = params.get("resource_name").getAsString().toLowerCase();

        logger.debug("Finding resources by name: " + projectName + " / " + resourceName);

        String gatewayDataPath = settings.getGatewayDataPath();
        if (gatewayDataPath == null || gatewayDataPath.isEmpty()) {
            throw new IllegalStateException("Gateway data path is not configured");
        }

        File projectDir = new File(gatewayDataPath, "projects/" + projectName);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            throw new IllegalArgumentException("Project not found: " + projectName);
        }

        List<ResourceMatch> matches = new ArrayList<>();
        findResources(projectDir, "", resourceName, matches);

        JsonObject result = new JsonObject();
        result.addProperty("project_name", projectName);
        result.addProperty("resource_name", resourceName);

        JsonArray matchesArray = new JsonArray();
        for (ResourceMatch match : matches) {
            JsonObject matchObj = new JsonObject();
            matchObj.addProperty("path", match.path);
            matchObj.addProperty("type", match.type);
            matchObj.addProperty("name", match.name);
            matchesArray.add(matchObj);
        }
        result.add("matches", matchesArray);
        result.addProperty("count", matches.size());

        return result;
    }

    private void findResources(File dir, String basePath, String searchTerm, List<ResourceMatch> matches) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String fileName = file.getName().toLowerCase();

            if (file.isDirectory()) {
                // Check if this is a view directory (has view.json)
                File viewJson = new File(file, "view.json");
                if (viewJson.exists() && fileName.contains(searchTerm)) {
                    String path = basePath.isEmpty() ? file.getName() : basePath + "/" + file.getName();
                    matches.add(new ResourceMatch(path, "perspective_view", file.getName()));
                }

                String subPath = basePath.isEmpty() ? file.getName() : basePath + "/" + file.getName();
                findResources(file, subPath, searchTerm, matches);
            } else {
                if (fileName.contains(searchTerm)) {
                    String path = basePath.isEmpty() ? file.getName() : basePath + "/" + file.getName();
                    String type = "file";

                    if (fileName.endsWith(".py")) type = "script_module";
                    else if (fileName.endsWith(".sql")) type = "named_query";
                    else if (fileName.endsWith(".json")) type = "json_resource";

                    matches.add(new ResourceMatch(path, type, file.getName()));
                }

                // Limit results
                if (matches.size() >= 50) {
                    return;
                }
            }
        }
    }

    private static class ResourceMatch {
        String path;
        String type;
        String name;

        ResourceMatch(String path, String type, String name) {
            this.path = path;
            this.type = type;
            this.name = name;
        }
    }
}
