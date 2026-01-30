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
 * Tool for listing named queries in a project.
 */
public class ListNamedQueriesTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.filesystem.ListNamedQueriesTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public ListNamedQueriesTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "list_named_queries";
    }

    @Override
    public String getDescription() {
        return "List named queries in an Ignition project. Returns .sql file paths.";
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

        JsonObject operation = new JsonObject();
        operation.addProperty("type", "string");
        operation.addProperty("description", "Optional filter by operation type (SELECT, INSERT, UPDATE, DELETE)");
        properties.add("operation", operation);

        JsonObject path = new JsonObject();
        path.addProperty("type", "string");
        path.addProperty("description", "Optional subdirectory path (default: root)");
        path.addProperty("default", "");
        properties.add("path", path);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("project_name");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String projectName = params.get("project_name").getAsString();
        String operation = params.has("operation") ? params.get("operation").getAsString() : null;
        String path = params.has("path") ? params.get("path").getAsString() : "";

        logger.debug("Listing named queries for: " + projectName + " (path: " + path + ", operation: " + operation + ")");

        String gatewayDataPath = settings.getGatewayDataPath();
        if (gatewayDataPath == null || gatewayDataPath.isEmpty()) {
            throw new IllegalStateException("Gateway data path is not configured");
        }

        File queriesDir = new File(gatewayDataPath, "projects/" + projectName + "/com.inductiveautomation.sqlbridge/" + path);
        if (!queriesDir.exists() || !queriesDir.isDirectory()) {
            throw new IllegalArgumentException("Named queries directory not found: " + path);
        }

        List<String> queries = new ArrayList<>();
        collectQueries(queriesDir, path, queries);

        JsonObject result = new JsonObject();
        result.addProperty("project_name", projectName);
        result.addProperty("path", path);

        JsonArray queriesArray = new JsonArray();
        for (String query : queries) {
            queriesArray.add(query);
        }
        result.add("queries", queriesArray);
        result.addProperty("count", queries.size());

        return result;
    }

    private void collectQueries(File dir, String basePath, List<String> queries) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                String subPath = basePath.isEmpty() ? file.getName() : basePath + "/" + file.getName();
                collectQueries(file, subPath, queries);
            } else if (file.getName().endsWith(".sql")) {
                String queryPath = basePath.isEmpty() ? file.getName() : basePath + "/" + file.getName();
                queries.add(queryPath);
            }
        }
    }
}
