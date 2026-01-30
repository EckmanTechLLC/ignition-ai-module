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
 * Tool for listing Perspective views in a project.
 */
public class ListPerspectiveViewsTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.filesystem.ListPerspectiveViewsTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public ListPerspectiveViewsTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "list_perspective_views";
    }

    @Override
    public String getDescription() {
        return "List all Perspective views in an Ignition project. Returns view names and paths.";
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

        JsonObject path = new JsonObject();
        path.addProperty("type", "string");
        path.addProperty("description", "Optional subdirectory path within views (default: root)");
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
        String path = params.has("path") ? params.get("path").getAsString() : "";

        logger.debug("Listing Perspective views for: " + projectName + " (path: " + path + ")");

        String gatewayDataPath = settings.getGatewayDataPath();
        if (gatewayDataPath == null || gatewayDataPath.isEmpty()) {
            throw new IllegalStateException("Gateway data path is not configured");
        }

        File viewsDir = new File(gatewayDataPath, "projects/" + projectName + "/com.inductiveautomation.perspective/views/" + path);
        if (!viewsDir.exists() || !viewsDir.isDirectory()) {
            throw new IllegalArgumentException("Views directory not found: " + path);
        }

        List<String> views = new ArrayList<>();
        collectViews(viewsDir, path, views);

        JsonObject result = new JsonObject();
        result.addProperty("project_name", projectName);
        result.addProperty("path", path);

        JsonArray viewsArray = new JsonArray();
        for (String view : views) {
            viewsArray.add(view);
        }
        result.add("views", viewsArray);
        result.addProperty("count", views.size());

        return result;
    }

    private void collectViews(File dir, String basePath, List<String> views) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                String subPath = basePath.isEmpty() ? file.getName() : basePath + "/" + file.getName();
                collectViews(file, subPath, views);
            } else if (file.getName().equals("view.json")) {
                views.add(basePath);
            }
        }
    }
}
