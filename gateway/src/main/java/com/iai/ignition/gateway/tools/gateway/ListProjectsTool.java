package com.iai.ignition.gateway.tools.gateway;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Tool for listing all Ignition projects in the Gateway.
 * Reads project directories from the Gateway's data/projects directory.
 */
public class ListProjectsTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.gateway.ListProjectsTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public ListProjectsTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "list_projects";
    }

    @Override
    public String getDescription() {
        return "List all Ignition projects in the Gateway. " +
                "Returns project names and basic information about each project.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        // No parameters needed
        JsonObject properties = new JsonObject();
        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        logger.debug("Listing Gateway projects");

        String gatewayDataPath = settings.getGatewayDataPath();
        if (gatewayDataPath == null || gatewayDataPath.isEmpty()) {
            throw new Exception("Gateway data path not configured in settings");
        }

        Path projectsDir = Paths.get(gatewayDataPath, "projects");
        if (!Files.exists(projectsDir)) {
            throw new Exception("Projects directory not found: " + projectsDir.toString());
        }

        if (!Files.isDirectory(projectsDir)) {
            throw new Exception("Projects path is not a directory: " + projectsDir.toString());
        }

        JsonArray projectArray = new JsonArray();

        try (Stream<Path> paths = Files.list(projectsDir)) {
            paths.filter(Files::isDirectory)
                 .forEach(path -> {
                     try {
                         File projectDir = path.toFile();
                         String projectName = projectDir.getName();

                         JsonObject projectObj = new JsonObject();
                         projectObj.addProperty("name", projectName);
                         projectObj.addProperty("path", path.toString());

                         // Check for common project subdirectories to determine type
                         boolean hasPerspective = Files.exists(path.resolve("com.inductiveautomation.perspective"));
                         boolean hasVision = Files.exists(path.resolve("com.inductiveautomation.vision"));

                         if (hasPerspective && hasVision) {
                             projectObj.addProperty("type", "Perspective + Vision");
                         } else if (hasPerspective) {
                             projectObj.addProperty("type", "Perspective");
                         } else if (hasVision) {
                             projectObj.addProperty("type", "Vision");
                         } else {
                             projectObj.addProperty("type", "Unknown");
                         }

                         // Check for script modules
                         Path scriptPath = path.resolve("ignition").resolve("script-python");
                         boolean hasScripts = Files.exists(scriptPath);
                         projectObj.addProperty("hasScripts", hasScripts);

                         // Check for named queries
                         Path queriesPath = path.resolve("com.inductiveautomation.ignition.common.script.NamedQuery");
                         boolean hasQueries = Files.exists(queriesPath);
                         projectObj.addProperty("hasNamedQueries", hasQueries);

                         projectArray.add(projectObj);
                     } catch (Exception e) {
                         logger.warn("Error reading project: " + path, e);
                     }
                 });
        } catch (Exception e) {
            logger.error("Error listing projects directory", e);
            throw new Exception("Failed to list projects: " + e.getMessage());
        }

        JsonObject result = new JsonObject();
        result.add("projects", projectArray);
        result.addProperty("count", projectArray.size());
        result.addProperty("projectsDirectory", projectsDir.toString());

        logger.debug("Found " + projectArray.size() + " project(s)");

        return result;
    }
}
