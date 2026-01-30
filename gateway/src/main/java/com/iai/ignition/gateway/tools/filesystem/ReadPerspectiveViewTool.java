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
 * Tool for reading a Perspective view's JSON configuration.
 */
public class ReadPerspectiveViewTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.filesystem.ReadPerspectiveViewTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public ReadPerspectiveViewTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "read_perspective_view";
    }

    @Override
    public String getDescription() {
        return "Read a Perspective view's JSON configuration. Returns the view.json content with metadata.";
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

        JsonObject viewPath = new JsonObject();
        viewPath.addProperty("type", "string");
        viewPath.addProperty("description", "Path to the view (e.g., 'MainView' or 'Folder/SubView')");
        properties.add("view_path", viewPath);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("project_name");
        required.add("view_path");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String projectName = params.get("project_name").getAsString();
        String viewPath = params.get("view_path").getAsString();

        logger.debug("Reading Perspective view: " + projectName + "/" + viewPath);

        String gatewayDataPath = settings.getGatewayDataPath();
        if (gatewayDataPath == null || gatewayDataPath.isEmpty()) {
            throw new IllegalStateException("Gateway data path is not configured");
        }

        File viewFile = new File(gatewayDataPath, "projects/" + projectName + "/com.inductiveautomation.perspective/views/" + viewPath + "/view.json");
        if (!viewFile.exists() || !viewFile.isFile()) {
            throw new IllegalArgumentException("View not found: " + viewPath);
        }

        String content = new String(Files.readAllBytes(viewFile.toPath()));

        JsonObject result = new JsonObject();
        result.addProperty("project_name", projectName);
        result.addProperty("view_path", viewPath);
        result.addProperty("size", viewFile.length());
        result.addProperty("content", content);

        return result;
    }
}
