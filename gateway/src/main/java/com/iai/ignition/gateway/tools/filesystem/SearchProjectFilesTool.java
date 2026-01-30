package com.iai.ignition.gateway.tools.filesystem;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tool for searching project files for text content (grep-like).
 */
public class SearchProjectFilesTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.filesystem.SearchProjectFilesTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public SearchProjectFilesTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "search_project_files";
    }

    @Override
    public String getDescription() {
        return "Search for text within project files. Returns matching files with excerpts showing the context of matches.";
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

        JsonObject fileTypes = new JsonObject();
        fileTypes.addProperty("type", "array");
        fileTypes.addProperty("description", "File extensions to search (default: ['.py', '.json', '.sql'])");
        JsonObject items = new JsonObject();
        items.addProperty("type", "string");
        fileTypes.add("items", items);
        properties.add("file_types", fileTypes);

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

        List<String> fileTypes = new ArrayList<>();
        if (params.has("file_types")) {
            JsonArray fileTypesArray = params.getAsJsonArray("file_types");
            for (int i = 0; i < fileTypesArray.size(); i++) {
                fileTypes.add(fileTypesArray.get(i).getAsString());
            }
        } else {
            fileTypes = Arrays.asList(".py", ".json", ".sql");
        }

        logger.debug("Searching project files: " + projectName + " for query: " + query);

        String gatewayDataPath = settings.getGatewayDataPath();
        if (gatewayDataPath == null || gatewayDataPath.isEmpty()) {
            throw new IllegalStateException("Gateway data path is not configured");
        }

        File projectDir = new File(gatewayDataPath, "projects/" + projectName);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            throw new IllegalArgumentException("Project not found: " + projectName);
        }

        List<SearchResult> results = new ArrayList<>();
        searchFiles(projectDir, "", query, fileTypes, results);

        JsonObject result = new JsonObject();
        result.addProperty("project_name", projectName);
        result.addProperty("query", query);

        JsonArray resultsArray = new JsonArray();
        for (SearchResult sr : results) {
            JsonObject match = new JsonObject();
            match.addProperty("file_path", sr.filePath);
            match.addProperty("line_number", sr.lineNumber);
            match.addProperty("excerpt", sr.excerpt);
            resultsArray.add(match);
        }
        result.add("matches", resultsArray);
        result.addProperty("count", results.size());

        return result;
    }

    private void searchFiles(File dir, String basePath, String query, List<String> fileTypes, List<SearchResult> results) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                String subPath = basePath.isEmpty() ? file.getName() : basePath + "/" + file.getName();
                searchFiles(file, subPath, query, fileTypes, results);
            } else {
                boolean matchesType = false;
                for (String ext : fileTypes) {
                    if (file.getName().endsWith(ext)) {
                        matchesType = true;
                        break;
                    }
                }

                if (matchesType) {
                    String filePath = basePath.isEmpty() ? file.getName() : basePath + "/" + file.getName();
                    searchInFile(file, filePath, query, results);
                }
            }
        }
    }

    private void searchInFile(File file, String filePath, String query, List<SearchResult> results) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.toLowerCase().contains(query.toLowerCase())) {
                    results.add(new SearchResult(filePath, lineNumber, line.trim()));

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

        SearchResult(String filePath, int lineNumber, String excerpt) {
            this.filePath = filePath;
            this.lineNumber = lineNumber;
            this.excerpt = excerpt;
        }
    }
}
