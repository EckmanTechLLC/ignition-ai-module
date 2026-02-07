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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Tool for searching files in the Gateway installation by pattern.
 * Provides flexible filesystem access for discovering modules, configs, logs, etc.
 */
public class SearchGatewayFilesTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.filesystem.SearchGatewayFilesTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public SearchGatewayFilesTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "search_gateway_files";
    }

    @Override
    public String getDescription() {
        return "Search for files in the Ignition Gateway installation by pattern. " +
                "Returns matching files with metadata (name, path, size). " +
                "Common locations: " +
                "modules in 'user-lib/modules/' (*.modl files), " +
                "configs in 'data/' (*.xml, *.json), " +
                "logs in 'logs/' (*.log), " +
                "backups in 'data/backup/', " +
                "projects in 'data/projects/'. " +
                "Use pattern matching (e.g., '*.modl', '*.log', 'myfile*') to find specific file types.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject pattern = new JsonObject();
        pattern.addProperty("type", "string");
        pattern.addProperty("description", "File pattern to search for (e.g., '*.modl', '*.log', 'config.xml'). Supports wildcards.");
        properties.add("pattern", pattern);

        JsonObject searchPath = new JsonObject();
        searchPath.addProperty("type", "string");
        searchPath.addProperty("description", "Optional subdirectory to search within (e.g., 'user-lib/modules', 'logs', 'data'). If omitted, searches from gateway root.");
        properties.add("search_path", searchPath);

        JsonObject recursive = new JsonObject();
        recursive.addProperty("type", "boolean");
        recursive.addProperty("description", "Whether to search recursively in subdirectories (default: true)");
        recursive.addProperty("default", true);
        properties.add("recursive", recursive);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("pattern");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String pattern = params.get("pattern").getAsString();
        String searchPath = params.has("search_path") ? params.get("search_path").getAsString() : "";
        boolean recursive = params.has("recursive") ? params.get("recursive").getAsBoolean() : true;

        logger.debug("Searching gateway files: pattern=" + pattern + ", searchPath=" + searchPath + ", recursive=" + recursive);

        String gatewayDataPath = settings.getGatewayDataPath();
        if (gatewayDataPath == null || gatewayDataPath.isEmpty()) {
            throw new IllegalStateException("Gateway data path is not configured");
        }

        // Determine search root
        Path searchRoot;
        if (searchPath != null && !searchPath.isEmpty()) {
            // Search in specified subdirectory
            searchRoot = Paths.get(gatewayDataPath, searchPath);
            if (!Files.exists(searchRoot)) {
                // Try relative to gateway home (parent of data)
                searchRoot = Paths.get(gatewayDataPath, "..", searchPath);
                if (!Files.exists(searchRoot)) {
                    throw new IllegalArgumentException("Search path not found: " + searchPath);
                }
            }
        } else {
            // Search from gateway data root
            searchRoot = Paths.get(gatewayDataPath);
        }

        List<FileMatch> matches = new ArrayList<>();
        searchFiles(searchRoot, searchRoot, pattern, recursive, matches);

        JsonObject result = new JsonObject();
        result.addProperty("pattern", pattern);
        result.addProperty("search_path", searchRoot.toString());
        result.addProperty("recursive", recursive);

        JsonArray matchesArray = new JsonArray();
        for (FileMatch match : matches) {
            JsonObject matchObj = new JsonObject();
            matchObj.addProperty("name", match.name);
            matchObj.addProperty("path", match.path);
            matchObj.addProperty("relative_path", match.relativePath);
            matchObj.addProperty("size_bytes", match.sizeBytes);
            matchObj.addProperty("size_formatted", match.sizeFormatted);
            matchObj.addProperty("is_directory", match.isDirectory);
            matchesArray.add(matchObj);
        }
        result.add("matches", matchesArray);
        result.addProperty("count", matches.size());

        logger.debug("Found " + matches.size() + " match(es)");

        return result;
    }

    private void searchFiles(Path searchRoot, Path currentDir, String pattern, boolean recursive, List<FileMatch> matches) {
        File[] files = currentDir.toFile().listFiles();
        if (files == null) return;

        for (File file : files) {
            // Check if we've hit the limit
            if (matches.size() >= 200) {
                return;
            }

            if (file.isDirectory() && recursive) {
                searchFiles(searchRoot, file.toPath(), pattern, recursive, matches);
            }

            // Check if filename matches pattern
            if (matchesPattern(file.getName(), pattern)) {
                String relativePath = searchRoot.relativize(file.toPath()).toString();
                matches.add(new FileMatch(
                        file.getName(),
                        file.getAbsolutePath(),
                        relativePath,
                        file.length(),
                        formatSize(file.length()),
                        file.isDirectory()
                ));
            }
        }
    }

    private boolean matchesPattern(String fileName, String pattern) {
        // Convert wildcard pattern to regex
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
        return fileName.matches(regex);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        } else {
            return (bytes / (1024 * 1024)) + " MB";
        }
    }

    private static class FileMatch {
        String name;
        String path;
        String relativePath;
        long sizeBytes;
        String sizeFormatted;
        boolean isDirectory;

        FileMatch(String name, String path, String relativePath, long sizeBytes, String sizeFormatted, boolean isDirectory) {
            this.name = name;
            this.path = path;
            this.relativePath = relativePath;
            this.sizeBytes = sizeBytes;
            this.sizeFormatted = sizeFormatted;
            this.isDirectory = isDirectory;
        }
    }
}
