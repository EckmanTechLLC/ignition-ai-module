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
import java.nio.file.Files;

/**
 * Tool for getting metadata about a project file.
 */
public class GetFileMetadataTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.filesystem.GetFileMetadataTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public GetFileMetadataTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "get_file_metadata";
    }

    @Override
    public String getDescription() {
        return "Get metadata about a file in an Ignition project including size, line count, last modified time, " +
                "and a structure summary. Use this before reading large files to understand their content.";
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

        JsonObject filePath = new JsonObject();
        filePath.addProperty("type", "string");
        filePath.addProperty("description", "Relative path to the file within the project");
        properties.add("file_path", filePath);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("project_name");
        required.add("file_path");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String projectName = params.get("project_name").getAsString();
        String filePath = params.get("file_path").getAsString();

        logger.debug("Getting file metadata for: " + projectName + "/" + filePath);

        String gatewayDataPath = settings.getGatewayDataPath();
        if (gatewayDataPath == null || gatewayDataPath.isEmpty()) {
            throw new IllegalStateException("Gateway data path is not configured");
        }

        File file = new File(gatewayDataPath, "projects/" + projectName + "/" + filePath);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("File not found: " + filePath);
        }

        JsonObject result = new JsonObject();
        result.addProperty("file_path", filePath);
        result.addProperty("size", file.length());
        result.addProperty("last_modified", file.lastModified());

        // Count lines
        int lineCount = countLines(file);
        result.addProperty("line_count", lineCount);

        // Determine file type
        String fileName = file.getName();
        String type = determineFileType(fileName);
        result.addProperty("type", type);

        // Add structure summary based on type
        String structureSummary = getStructureSummary(file, type);
        if (structureSummary != null) {
            result.addProperty("structure_summary", structureSummary);
        }

        return result;
    }

    private int countLines(File file) {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            while (reader.readLine() != null) {
                count++;
            }
        } catch (Exception e) {
            logger.warn("Error counting lines in file", e);
        }
        return count;
    }

    private String determineFileType(String fileName) {
        if (fileName.endsWith(".json")) return "json";
        if (fileName.endsWith(".py")) return "python";
        if (fileName.endsWith(".sql")) return "sql";
        if (fileName.endsWith(".xml")) return "xml";
        if (fileName.endsWith(".txt")) return "text";
        return "unknown";
    }

    private String getStructureSummary(File file, String type) {
        try {
            if ("json".equals(type)) {
                // For JSON files, we could parse and return key structure
                return "JSON file - use read_file_content to see structure";
            } else if ("python".equals(type)) {
                // For Python files, we could extract function/class names
                return "Python module - use read_file_content to see functions and classes";
            } else if ("sql".equals(type)) {
                // For SQL files, we could extract query type
                return "SQL file - use read_file_content to see query";
            }
        } catch (Exception e) {
            logger.warn("Error generating structure summary", e);
        }
        return null;
    }
}
