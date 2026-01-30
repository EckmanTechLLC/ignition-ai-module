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

/**
 * Tool for reading file content with line numbers.
 * Enforces maxToolResultSizeKB from settings.
 */
public class ReadFileContentTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.filesystem.ReadFileContentTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public ReadFileContentTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "read_file_content";
    }

    @Override
    public String getDescription() {
        return "Read the content of a file in an Ignition project. Returns content with line numbers in cat -n format. " +
                "Supports offset and limit parameters for reading specific portions of large files.";
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

        JsonObject offset = new JsonObject();
        offset.addProperty("type", "integer");
        offset.addProperty("description", "Line number to start reading from (default: 0)");
        offset.addProperty("default", 0);
        properties.add("offset", offset);

        JsonObject limit = new JsonObject();
        limit.addProperty("type", "integer");
        limit.addProperty("description", "Maximum number of lines to read (default: 1000)");
        limit.addProperty("default", 1000);
        properties.add("limit", limit);

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
        int offset = params.has("offset") ? params.get("offset").getAsInt() : 0;
        int limit = params.has("limit") ? params.get("limit").getAsInt() : 1000;

        logger.debug("Reading file content: " + projectName + "/" + filePath + " (offset: " + offset + ", limit: " + limit + ")");

        String gatewayDataPath = settings.getGatewayDataPath();
        if (gatewayDataPath == null || gatewayDataPath.isEmpty()) {
            throw new IllegalStateException("Gateway data path is not configured");
        }

        File file = new File(gatewayDataPath, "projects/" + projectName + "/" + filePath);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("File not found: " + filePath);
        }

        // Read file with line numbers
        StringBuilder content = new StringBuilder();
        int currentLine = 0;
        int linesRead = 0;
        boolean truncated = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                currentLine++;

                // Skip lines before offset
                if (currentLine <= offset) {
                    continue;
                }

                // Check if we've reached the limit
                if (linesRead >= limit) {
                    truncated = true;
                    break;
                }

                // Check size limit (maxToolResultSizeKB)
                int maxSizeBytes = settings.getMaxToolResultSizeKB() * 1024;
                if (content.length() + line.length() > maxSizeBytes) {
                    truncated = true;
                    break;
                }

                // Add line with number (cat -n format)
                content.append(String.format("%6d\t%s\n", currentLine, line));
                linesRead++;
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("file_path", filePath);
        result.addProperty("offset", offset);
        result.addProperty("lines_read", linesRead);
        result.addProperty("content", content.toString());
        result.addProperty("truncated", truncated);

        if (truncated) {
            result.addProperty("message", "Content truncated due to size or line limit. Use offset parameter to read more.");
        }

        return result;
    }
}
