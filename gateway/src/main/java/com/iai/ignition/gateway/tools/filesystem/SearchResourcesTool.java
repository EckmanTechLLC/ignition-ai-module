package com.iai.ignition.gateway.tools.filesystem;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Consolidated tool for searching resources.
 * Replaces: search_project_files, find_resource_by_name,
 * search_project_resources, search_gateway_files.
 */
public class SearchResourcesTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.filesystem.SearchResourcesTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public SearchResourcesTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "search_resources";
    }

    @Override
    public String getDescription() {
        return "Search for files and resources by name or pattern. " +
               "Scopes: project (search project files), gateway (search Gateway installation), " +
               "resource (find views/scripts/queries by name). " +
               "Supports wildcards and pattern matching.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject query = new JsonObject();
        query.addProperty("type", "string");
        query.addProperty("description", "Search query (filename pattern, resource name, or keyword)");
        properties.add("query", query);

        JsonObject scope = new JsonObject();
        scope.addProperty("type", "string");
        scope.addProperty("description", "Search scope");
        JsonArray scopeEnum = new JsonArray();
        scopeEnum.add("project");
        scopeEnum.add("gateway");
        scopeEnum.add("resource");
        scope.add("enum", scopeEnum);
        scope.addProperty("default", "project");
        properties.add("scope", scope);

        JsonObject projectName = new JsonObject();
        projectName.addProperty("type", "string");
        projectName.addProperty("description", "Project name (required for project scope)");
        properties.add("project_name", projectName);

        JsonObject resourceType = new JsonObject();
        resourceType.addProperty("type", "string");
        resourceType.addProperty("description", "Resource type to search: view, script, query (for resource scope)");
        properties.add("resource_type", resourceType);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("query");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String query = params.get("query").getAsString().toLowerCase();
        String scope = params.has("scope") ? params.get("scope").getAsString() : "project";

        logger.debug("search_resources: query=" + query + ", scope=" + scope);

        String gatewayDataPath = settings.getGatewayDataPath();
        if (gatewayDataPath == null || gatewayDataPath.isEmpty()) {
            gatewayDataPath = System.getProperty("ignition.home", "/usr/local/bin/ignition") + "/data";
        }

        switch (scope) {
            case "project":
                if (!params.has("project_name")) {
                    throw new IllegalArgumentException("project_name required for project scope");
                }
                return searchProject(query, gatewayDataPath, params.get("project_name").getAsString());

            case "gateway":
                return searchGateway(query, gatewayDataPath);

            case "resource":
                if (!params.has("project_name") || !params.has("resource_type")) {
                    throw new IllegalArgumentException("project_name and resource_type required for resource scope");
                }
                return searchResource(query, gatewayDataPath, params.get("project_name").getAsString(),
                                    params.get("resource_type").getAsString());

            default:
                throw new IllegalArgumentException("Unknown scope: " + scope);
        }
    }

    private JsonObject searchProject(String query, String gatewayDataPath, String projectName) throws Exception {
        Path projectPath = Paths.get(gatewayDataPath, "projects", projectName);

        if (!Files.exists(projectPath)) {
            throw new IllegalArgumentException("Project not found: " + projectName);
        }

        JsonArray matches = new JsonArray();

        try (Stream<Path> paths = Files.walk(projectPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.getFileName().toString().toLowerCase().contains(query))
                 .limit(100)
                 .forEach(p -> {
                     JsonObject match = new JsonObject();
                     match.addProperty("path", projectPath.relativize(p).toString());
                     match.addProperty("name", p.getFileName().toString());
                     matches.add(match);
                 });
        }

        JsonObject result = new JsonObject();
        result.addProperty("query", query);
        result.addProperty("scope", "project");
        result.addProperty("project", projectName);
        result.addProperty("matches", matches.size());
        result.add("results", matches);

        return result;
    }

    private JsonObject searchGateway(String query, String gatewayDataPath) throws Exception {
        Path gatewayPath = Paths.get(gatewayDataPath).getParent();

        JsonArray matches = new JsonArray();

        try (Stream<Path> paths = Files.walk(gatewayPath, 3)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.getFileName().toString().toLowerCase().contains(query))
                 .limit(50)
                 .forEach(p -> {
                     JsonObject match = new JsonObject();
                     match.addProperty("path", p.toString());
                     match.addProperty("name", p.getFileName().toString());
                     matches.add(match);
                 });
        }

        JsonObject result = new JsonObject();
        result.addProperty("query", query);
        result.addProperty("scope", "gateway");
        result.addProperty("matches", matches.size());
        result.add("results", matches);

        return result;
    }

    private JsonObject searchResource(String query, String gatewayDataPath, String projectName, String resourceType) throws Exception {
        Path projectPath = Paths.get(gatewayDataPath, "projects", projectName);

        if (!Files.exists(projectPath)) {
            throw new IllegalArgumentException("Project not found: " + projectName);
        }

        Path searchPath;
        switch (resourceType) {
            case "view":
                searchPath = projectPath.resolve("com.inductiveautomation.perspective/views");
                break;
            case "script":
                searchPath = projectPath.resolve("ignition/script-python");
                break;
            case "query":
                searchPath = projectPath.resolve("com.inductiveautomation.ignition.common.script.data/queries");
                break;
            default:
                throw new IllegalArgumentException("Unknown resource type: " + resourceType);
        }

        JsonArray matches = new JsonArray();

        if (Files.exists(searchPath)) {
            try (Stream<Path> paths = Files.walk(searchPath)) {
                paths.filter(p -> p.getFileName().toString().toLowerCase().contains(query))
                     .limit(50)
                     .forEach(p -> {
                         JsonObject match = new JsonObject();
                         match.addProperty("path", searchPath.relativize(p).toString());
                         match.addProperty("name", p.getFileName().toString());
                         match.addProperty("type", resourceType);
                         matches.add(match);
                     });
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("query", query);
        result.addProperty("scope", "resource");
        result.addProperty("resource_type", resourceType);
        result.addProperty("project", projectName);
        result.addProperty("matches", matches.size());
        result.add("results", matches);

        return result;
    }
}
