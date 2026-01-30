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
 * Tool for reading a named query SQL file.
 */
public class ReadNamedQueryTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.filesystem.ReadNamedQueryTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public ReadNamedQueryTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "read_named_query";
    }

    @Override
    public String getDescription() {
        return "Read a named query SQL file from an Ignition project. Returns the SQL content.";
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

        JsonObject queryPath = new JsonObject();
        queryPath.addProperty("type", "string");
        queryPath.addProperty("description", "Path to the query (e.g., 'GetUsers.sql' or 'folder/Query.sql')");
        properties.add("query_path", queryPath);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("project_name");
        required.add("query_path");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String projectName = params.get("project_name").getAsString();
        String queryPath = params.get("query_path").getAsString();

        logger.debug("Reading named query: " + projectName + "/" + queryPath);

        String gatewayDataPath = settings.getGatewayDataPath();
        if (gatewayDataPath == null || gatewayDataPath.isEmpty()) {
            throw new IllegalStateException("Gateway data path is not configured");
        }

        File queryFile = new File(gatewayDataPath, "projects/" + projectName + "/com.inductiveautomation.sqlbridge/" + queryPath);
        if (!queryFile.exists() || !queryFile.isFile()) {
            throw new IllegalArgumentException("Named query not found: " + queryPath);
        }

        String content = new String(Files.readAllBytes(queryFile.toPath()));

        JsonObject result = new JsonObject();
        result.addProperty("project_name", projectName);
        result.addProperty("query_path", queryPath);
        result.addProperty("size", queryFile.length());
        result.addProperty("content", content);

        return result;
    }
}
