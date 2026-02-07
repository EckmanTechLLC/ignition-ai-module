package com.iai.ignition.gateway.tools.filesystem;

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
 * Consolidated tool for project file operations.
 * Replaces: get_project_structure, get_file_metadata, read_file_content,
 * list_perspective_views, read_perspective_view, read_vision_window,
 * list_script_modules, read_script_module, list_named_queries, read_named_query.
 */
public class ProjectFilesTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.filesystem.ProjectFilesTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public ProjectFilesTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "project_files";
    }

    @Override
    public String getDescription() {
        return "Read and list project files and resources. " +
               "Actions: list_views, read_view, list_scripts, read_script, list_queries, read_query, " +
               "read_file, get_structure, get_metadata. " +
               "Handles Perspective views, Vision windows, script modules, named queries, and raw files.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject action = new JsonObject();
        action.addProperty("type", "string");
        action.addProperty("description", "Action to perform");
        JsonArray actionEnum = new JsonArray();
        actionEnum.add("list_views");
        actionEnum.add("read_view");
        actionEnum.add("list_scripts");
        actionEnum.add("read_script");
        actionEnum.add("list_queries");
        actionEnum.add("read_query");
        actionEnum.add("read_file");
        actionEnum.add("get_structure");
        actionEnum.add("get_metadata");
        action.add("enum", actionEnum);
        properties.add("action", action);

        JsonObject projectName = new JsonObject();
        projectName.addProperty("type", "string");
        projectName.addProperty("description", "Project name");
        properties.add("project_name", projectName);

        JsonObject resourceType = new JsonObject();
        resourceType.addProperty("type", "string");
        resourceType.addProperty("description", "Resource type: perspective, vision (for views), python (for scripts)");
        properties.add("resource_type", resourceType);

        JsonObject resourceName = new JsonObject();
        resourceName.addProperty("type", "string");
        resourceName.addProperty("description", "Resource name or path");
        properties.add("resource_name", resourceName);

        JsonObject filePath = new JsonObject();
        filePath.addProperty("type", "string");
        filePath.addProperty("description", "File path relative to project root");
        properties.add("file_path", filePath);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("action");
        required.add("project_name");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String action = params.get("action").getAsString();
        String projectName = params.get("project_name").getAsString();

        logger.debug("project_files: action=" + action + ", project=" + projectName);

        String gatewayDataPath = settings.getGatewayDataPath();
        if (gatewayDataPath == null || gatewayDataPath.isEmpty()) {
            gatewayDataPath = System.getProperty("ignition.home", "/usr/local/bin/ignition") + "/data";
        }

        Path projectPath = Paths.get(gatewayDataPath, "projects", projectName);

        if (!Files.exists(projectPath)) {
            throw new IllegalArgumentException("Project not found: " + projectName);
        }

        switch (action) {
            case "list_views":
                return listViews(projectPath, params.has("resource_type") ? params.get("resource_type").getAsString() : "perspective");

            case "read_view":
                return readView(projectPath, params.get("resource_name").getAsString(),
                               params.has("resource_type") ? params.get("resource_type").getAsString() : "perspective");

            case "list_scripts":
                return listScripts(projectPath);

            case "read_script":
                return readScript(projectPath, params.get("resource_name").getAsString());

            case "list_queries":
                return listQueries(projectPath);

            case "read_query":
                return readQuery(projectPath, params.get("resource_name").getAsString());

            case "read_file":
                return readFile(projectPath, params.get("file_path").getAsString());

            case "get_structure":
                return getStructure(projectPath);

            case "get_metadata":
                return getMetadata(projectPath, params.get("file_path").getAsString());

            default:
                throw new IllegalArgumentException("Unknown action: " + action);
        }
    }

    private JsonObject listViews(Path projectPath, String resourceType) throws Exception {
        Path viewsPath = "perspective".equals(resourceType)
            ? projectPath.resolve("com.inductiveautomation.perspective/views")
            : projectPath.resolve("com.inductiveautomation.vision/windows");

        JsonArray views = new JsonArray();
        if (Files.exists(viewsPath)) {
            try (Stream<Path> paths = Files.walk(viewsPath, 2)) {
                paths.filter(p -> p.toString().endsWith(".json") || p.toString().endsWith(".xml"))
                     .forEach(p -> views.add(viewsPath.relativize(p).toString()));
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("type", resourceType);
        result.addProperty("count", views.size());
        result.add("views", views);
        return result;
    }

    private JsonObject readView(Path projectPath, String viewName, String resourceType) throws Exception {
        Path viewPath = "perspective".equals(resourceType)
            ? projectPath.resolve("com.inductiveautomation.perspective/views/" + viewName + "/view.json")
            : projectPath.resolve("com.inductiveautomation.vision/windows/" + viewName + ".xml");

        if (!Files.exists(viewPath)) {
            throw new IllegalArgumentException("View not found: " + viewName);
        }

        String content = new String(Files.readAllBytes(viewPath));

        JsonObject result = new JsonObject();
        result.addProperty("name", viewName);
        result.addProperty("type", resourceType);
        result.addProperty("content", content);
        return result;
    }

    private JsonObject listScripts(Path projectPath) throws Exception {
        Path scriptsPath = projectPath.resolve("ignition/script-python");

        JsonArray scripts = new JsonArray();
        if (Files.exists(scriptsPath)) {
            try (Stream<Path> paths = Files.walk(scriptsPath)) {
                paths.filter(p -> p.toString().endsWith(".py"))
                     .forEach(p -> scripts.add(scriptsPath.relativize(p).toString()));
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("count", scripts.size());
        result.add("scripts", scripts);
        return result;
    }

    private JsonObject readScript(Path projectPath, String scriptName) throws Exception {
        Path scriptPath = projectPath.resolve("ignition/script-python/" + scriptName);

        if (!Files.exists(scriptPath)) {
            throw new IllegalArgumentException("Script not found: " + scriptName);
        }

        String content = new String(Files.readAllBytes(scriptPath));

        JsonObject result = new JsonObject();
        result.addProperty("name", scriptName);
        result.addProperty("content", content);
        return result;
    }

    private JsonObject listQueries(Path projectPath) throws Exception {
        Path queriesPath = projectPath.resolve("com.inductiveautomation.ignition.common.script.data/queries");

        JsonArray queries = new JsonArray();
        if (Files.exists(queriesPath)) {
            try (Stream<Path> paths = Files.list(queriesPath)) {
                paths.filter(Files::isDirectory)
                     .forEach(p -> queries.add(p.getFileName().toString()));
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("count", queries.size());
        result.add("queries", queries);
        return result;
    }

    private JsonObject readQuery(Path projectPath, String queryName) throws Exception {
        Path queryPath = projectPath.resolve("com.inductiveautomation.ignition.common.script.data/queries/" + queryName + "/resource.json");

        if (!Files.exists(queryPath)) {
            throw new IllegalArgumentException("Query not found: " + queryName);
        }

        String content = new String(Files.readAllBytes(queryPath));

        JsonObject result = new JsonObject();
        result.addProperty("name", queryName);
        result.addProperty("content", content);
        return result;
    }

    private JsonObject readFile(Path projectPath, String filePath) throws Exception {
        Path file = projectPath.resolve(filePath);

        if (!Files.exists(file)) {
            throw new IllegalArgumentException("File not found: " + filePath);
        }

        String content = new String(Files.readAllBytes(file));

        JsonObject result = new JsonObject();
        result.addProperty("path", filePath);
        result.addProperty("content", content);
        result.addProperty("size", Files.size(file));
        return result;
    }

    private JsonObject getStructure(Path projectPath) throws Exception {
        JsonArray structure = new JsonArray();

        if (Files.exists(projectPath)) {
            try (Stream<Path> paths = Files.walk(projectPath, 2)) {
                paths.filter(Files::isDirectory)
                     .forEach(p -> structure.add(projectPath.relativize(p).toString()));
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("count", structure.size());
        result.add("directories", structure);
        return result;
    }

    private JsonObject getMetadata(Path projectPath, String filePath) throws Exception {
        Path file = projectPath.resolve(filePath);

        if (!Files.exists(file)) {
            throw new IllegalArgumentException("File not found: " + filePath);
        }

        JsonObject result = new JsonObject();
        result.addProperty("path", filePath);
        result.addProperty("size", Files.size(file));
        result.addProperty("is_directory", Files.isDirectory(file));
        result.addProperty("last_modified", Files.getLastModifiedTime(file).toMillis());
        return result;
    }
}
