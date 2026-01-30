package com.iai.ignition.gateway.tools.search;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

/**
 * Tool for searching across multiple resource types in a project.
 * Searches views, scripts, named queries, and optionally tags.
 */
public class SearchProjectResourcesTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.search.SearchProjectResourcesTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public SearchProjectResourcesTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "search_project_resources";
    }

    @Override
    public String getDescription() {
        return "Search across multiple resource types in a project (views, scripts, queries). Returns matches " +
                "with resource type, path, and context excerpts.";
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

        JsonObject query = new JsonObject();
        query.addProperty("type", "string");
        query.addProperty("description", "The text to search for");
        properties.add("query", query);

        JsonObject resourceTypes = new JsonObject();
        resourceTypes.addProperty("type", "array");
        resourceTypes.addProperty("description", "Resource types to search (views, scripts, queries). If empty, searches all.");
        JsonObject items = new JsonObject();
        items.addProperty("type", "string");
        resourceTypes.add("items", items);
        properties.add("resource_types", resourceTypes);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("project_name");
        required.add("query");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String projectName = params.get("project_name").getAsString();
        String query = params.get("query").getAsString();

        List<String> resourceTypes = new ArrayList<>();
        if (params.has("resource_types")) {
            JsonArray typesArray = params.getAsJsonArray("resource_types");
            for (int i = 0; i < typesArray.size(); i++) {
                resourceTypes.add(typesArray.get(i).getAsString());
            }
        } else {
            resourceTypes = Arrays.asList("views", "scripts", "queries");
        }

        logger.debug("Searching project resources for: " + query);

        // Map resource types to file extensions and paths
        Map<String, List<String>> resourceToExtensions = new HashMap<>();
        resourceToExtensions.put("views", Arrays.asList(".json"));
        resourceToExtensions.put("scripts", Arrays.asList(".py"));
        resourceToExtensions.put("queries", Arrays.asList(".sql"));

        // Collect all file extensions to search
        Set<String> fileExtensions = new HashSet<>();
        for (String resourceType : resourceTypes) {
            List<String> extensions = resourceToExtensions.get(resourceType);
            if (extensions != null) {
                fileExtensions.addAll(extensions);
            }
        }

        String gatewayDataPath = settings.getGatewayDataPath();
        if (gatewayDataPath == null || gatewayDataPath.isEmpty()) {
            throw new IllegalStateException("Gateway data path is not configured");
        }

        File projectDir = new File(gatewayDataPath, "projects/" + projectName);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            throw new IllegalArgumentException("Project not found: " + projectName);
        }

        List<SearchResult> results = new ArrayList<>();
        searchFiles(projectDir, "", query, new ArrayList<>(fileExtensions), results, resourceTypes);

        JsonObject result = new JsonObject();
        result.addProperty("project_name", projectName);
        result.addProperty("query", query);

        JsonArray resourceTypesArray = new JsonArray();
        for (String rt : resourceTypes) {
            resourceTypesArray.add(rt);
        }
        result.add("resource_types", resourceTypesArray);

        JsonArray resultsArray = new JsonArray();
        for (SearchResult sr : results) {
            JsonObject match = new JsonObject();
            match.addProperty("file_path", sr.filePath);
            match.addProperty("line_number", sr.lineNumber);
            match.addProperty("excerpt", sr.excerpt);
            match.addProperty("resource_type", sr.resourceType);
            resultsArray.add(match);
        }
        result.add("matches", resultsArray);
        result.addProperty("count", results.size());

        return result;
    }

    private void searchFiles(File dir, String basePath, String query, List<String> fileTypes,
                            List<SearchResult> results, List<String> resourceTypes) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                String subPath = basePath.isEmpty() ? file.getName() : basePath + "/" + file.getName();
                searchFiles(file, subPath, query, fileTypes, results, resourceTypes);
            } else {
                boolean matchesType = false;
                String resourceType = null;

                for (String ext : fileTypes) {
                    if (file.getName().endsWith(ext)) {
                        matchesType = true;
                        // Determine resource type from extension
                        if (ext.equals(".json")) {
                            resourceType = "view";
                        } else if (ext.equals(".py")) {
                            resourceType = "script";
                        } else if (ext.equals(".sql")) {
                            resourceType = "query";
                        }
                        break;
                    }
                }

                if (matchesType && resourceType != null) {
                    String filePath = basePath.isEmpty() ? file.getName() : basePath + "/" + file.getName();
                    searchInFile(file, filePath, query, results, resourceType);
                }
            }
        }
    }

    private void searchInFile(File file, String filePath, String query, List<SearchResult> results, String resourceType) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.toLowerCase().contains(query.toLowerCase())) {
                    results.add(new SearchResult(filePath, lineNumber, line.trim(), resourceType));

                    // Limit results to prevent large responses
                    if (results.size() >= 100) {
                        return;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error searching file: " + filePath, e);
        }
    }

    private static class SearchResult {
        String filePath;
        int lineNumber;
        String excerpt;
        String resourceType;

        SearchResult(String filePath, int lineNumber, String excerpt, String resourceType) {
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.excerpt = excerpt;
            this.resourceType = resourceType;
        }
    }
}
