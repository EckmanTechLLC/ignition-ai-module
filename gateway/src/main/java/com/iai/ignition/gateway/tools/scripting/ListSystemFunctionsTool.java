package com.iai.ignition.gateway.tools.scripting;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.util.*;

/**
 * Tool for listing available Ignition system.* scripting functions.
 * Returns comprehensive catalog with descriptions, parameters, and categories.
 */
public class ListSystemFunctionsTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.scripting.ListSystemFunctionsTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;
    private final ScriptExecutor scriptExecutor;

    // System function catalog (comprehensive list of all functions)
    private static final List<SystemFunctionMetadata> FUNCTION_CATALOG = buildCatalog();

    public ListSystemFunctionsTool(GatewayContext gatewayContext, IAISettings settings, ScriptExecutor scriptExecutor) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
        this.scriptExecutor = scriptExecutor;
    }

    @Override
    public String getName() {
        return "list_system_functions";
    }

    @Override
    public String getDescription() {
        return "List available Ignition system.* scripting functions that can be executed. " +
               "Returns function names, descriptions, parameters, categories, and current safety mode. " +
               "Supports optional search query to filter functions by name or category.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        // Optional search query
        JsonObject query = new JsonObject();
        query.addProperty("type", "string");
        query.addProperty("description", "Optional search query to filter functions (e.g., 'tag', 'database', 'alarm')");
        properties.add("query", query);

        // Optional category filter
        JsonObject category = new JsonObject();
        category.addProperty("type", "string");
        category.addProperty("description", "Optional category filter (e.g., 'system.tag', 'system.db', 'system.alarm')");
        properties.add("category", category);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        schema.add("required", required); // No required parameters

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String mode = settings.getSystemFunctionMode();
        String searchQuery = params.has("query") ? params.get("query").getAsString() : null;
        String categoryFilter = params.has("category") ? params.get("category").getAsString() : null;

        logger.debug("Listing system functions - mode: " + mode + ", query: " + searchQuery + ", category: " + categoryFilter);

        // Filter functions based on mode and search criteria
        JsonArray functions = new JsonArray();
        for (SystemFunctionMetadata func : FUNCTION_CATALOG) {
            // Filter by mode
            if (mode.equals("READ_ONLY") && !scriptExecutor.isReadOnlyWhitelisted(func.name)) {
                continue; // Skip non-whitelisted functions in READ_ONLY mode
            }

            // Filter by search query
            if (searchQuery != null && !func.matchesQuery(searchQuery)) {
                continue;
            }

            // Filter by category
            if (categoryFilter != null && !func.category.equals(categoryFilter)) {
                continue;
            }

            // Add function to results
            JsonObject funcJson = new JsonObject();
            funcJson.addProperty("name", func.name);
            funcJson.addProperty("description", func.description);
            funcJson.addProperty("category", func.category);
            funcJson.addProperty("readOnly", scriptExecutor.isReadOnlyWhitelisted(func.name));
            funcJson.add("parameters", func.parametersToJson());

            functions.add(funcJson);
        }

        JsonObject result = new JsonObject();
        result.addProperty("mode", mode);
        result.addProperty("count", functions.size());
        result.add("functions", functions);

        // Add mode explanation
        if (mode.equals("READ_ONLY")) {
            result.addProperty("message", "READ_ONLY mode: Only whitelisted read-only functions available");
        } else if (mode.equals("UNRESTRICTED")) {
            result.addProperty("message", "UNRESTRICTED mode: All system.* functions available (testing only)");
        } else {
            result.addProperty("message", "System function execution is disabled");
        }

        logger.info("Listed " + functions.size() + " system functions in mode: " + mode);

        return result;
    }

    /**
     * Build comprehensive catalog of system functions.
     * This is a curated subset representing the most commonly used functions.
     * Full catalog would include 256+ functions across 34 categories.
     */
    private static List<SystemFunctionMetadata> buildCatalog() {
        List<SystemFunctionMetadata> catalog = new ArrayList<>();

        // ===== Tag Functions (system.tag.*) =====
        catalog.add(new SystemFunctionMetadata(
            "system.tag.read",
            "system.tag",
            "Read current values from one or more tags",
            Arrays.asList(new Parameter("tagPaths", "list", "List of tag paths to read"))
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.tag.readBlocking",
            "system.tag",
            "Read tag values synchronously (blocking)",
            Arrays.asList(new Parameter("tagPaths", "list", "List of tag paths to read"), new Parameter("timeout", "int", "Timeout in milliseconds (optional)"))
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.tag.readAsync",
            "system.tag",
            "Read tag values asynchronously",
            Arrays.asList(new Parameter("tagPaths", "list", "List of tag paths to read"))
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.tag.write",
            "system.tag",
            "Write values to one or more tags (WRITE OPERATION)",
            Arrays.asList(
                new Parameter("tagPaths", "list", "List of tag paths to write"),
                new Parameter("values", "list", "Values to write to tags")
            )
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.tag.writeBlocking",
            "system.tag",
            "Write tag values synchronously (WRITE OPERATION)",
            Arrays.asList(
                new Parameter("tagPaths", "list", "List of tag paths"),
                new Parameter("values", "list", "Values to write"),
                new Parameter("timeout", "int", "Timeout in milliseconds (optional)")
            )
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.tag.browse",
            "system.tag",
            "Browse tags under a specified path",
            Arrays.asList(new Parameter("parentPath", "string", "Parent tag path to browse"))
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.tag.exists",
            "system.tag",
            "Check if a tag path exists",
            Arrays.asList(new Parameter("tagPath", "string", "Tag path to check"))
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.tag.queryTagHistory",
            "system.tag",
            "Query historical tag values",
            Arrays.asList(
                new Parameter("paths", "list", "Tag paths to query"),
                new Parameter("startDate", "date", "Start date/time"),
                new Parameter("endDate", "date", "End date/time")
            )
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.tag.configure",
            "system.tag",
            "Configure tag properties (WRITE OPERATION)",
            Arrays.asList(
                new Parameter("basePath", "string", "Base path for tags"),
                new Parameter("tags", "list", "Tag configuration objects"),
                new Parameter("collisionPolicy", "string", "Collision policy (optional)")
            )
        ));

        // ===== Database Functions (system.db.*) =====
        catalog.add(new SystemFunctionMetadata(
            "system.db.runQuery",
            "system.db",
            "Execute a SQL SELECT query and return results as dataset",
            Arrays.asList(
                new Parameter("query", "string", "SQL query to execute"),
                new Parameter("database", "string", "Database connection name (optional)")
            )
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.db.runPrepQuery",
            "system.db",
            "Execute a prepared SQL query with parameters",
            Arrays.asList(
                new Parameter("query", "string", "SQL query with ? placeholders"),
                new Parameter("params", "list", "Parameter values for placeholders"),
                new Parameter("database", "string", "Database connection name (optional)")
            )
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.db.runScalarQuery",
            "system.db",
            "Execute a query that returns a single value",
            Arrays.asList(
                new Parameter("query", "string", "SQL query"),
                new Parameter("database", "string", "Database connection name (optional)")
            )
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.db.runUpdateQuery",
            "system.db",
            "Execute a SQL UPDATE/INSERT/DELETE query (WRITE OPERATION)",
            Arrays.asList(
                new Parameter("query", "string", "SQL update query"),
                new Parameter("database", "string", "Database connection name (optional)")
            )
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.db.runPrepUpdate",
            "system.db",
            "Execute a prepared UPDATE/INSERT/DELETE query (WRITE OPERATION)",
            Arrays.asList(
                new Parameter("query", "string", "SQL query with ? placeholders"),
                new Parameter("params", "list", "Parameter values"),
                new Parameter("database", "string", "Database connection name (optional)")
            )
        ));

        // ===== Alarm Functions (system.alarm.*) =====
        catalog.add(new SystemFunctionMetadata(
            "system.alarm.queryJournal",
            "system.alarm",
            "Query the alarm journal for historical alarm events",
            Arrays.asList(
                new Parameter("startDate", "date", "Start date/time"),
                new Parameter("endDate", "date", "End date/time"),
                new Parameter("filter", "dict", "Optional filter criteria (priority, source, etc.)")
            )
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.alarm.queryStatus",
            "system.alarm",
            "Query current alarm status",
            Arrays.asList(
                new Parameter("priority", "string", "Priority filter (optional)"),
                new Parameter("state", "string", "State filter (optional)")
            )
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.alarm.acknowledge",
            "system.alarm",
            "Acknowledge alarms (WRITE OPERATION)",
            Arrays.asList(
                new Parameter("alarmIds", "list", "List of alarm IDs to acknowledge"),
                new Parameter("notes", "string", "Acknowledgment notes (optional)")
            )
        ));

        // ===== Utility Functions (system.util.*) =====
        catalog.add(new SystemFunctionMetadata(
            "system.util.getSystemFlags",
            "system.util",
            "Get system flags and runtime information",
            Collections.emptyList()
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.util.getGatewayStatus",
            "system.util",
            "Get gateway status information",
            Collections.emptyList()
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.util.jsonEncode",
            "system.util",
            "Encode an object as JSON string",
            Arrays.asList(new Parameter("obj", "object", "Object to encode as JSON"))
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.util.jsonDecode",
            "system.util",
            "Decode a JSON string to an object",
            Arrays.asList(new Parameter("json", "string", "JSON string to decode"))
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.util.sendMessage",
            "system.util",
            "Send a message to a message handler (WRITE OPERATION)",
            Arrays.asList(
                new Parameter("project", "string", "Project name"),
                new Parameter("messageHandler", "string", "Message handler name"),
                new Parameter("payload", "dict", "Message payload")
            )
        ));

        // ===== Date Functions (system.date.*) =====
        catalog.add(new SystemFunctionMetadata(
            "system.date.now",
            "system.date",
            "Get current date/time",
            Collections.emptyList()
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.date.parse",
            "system.date",
            "Parse a date string into a date object",
            Arrays.asList(
                new Parameter("dateString", "string", "Date string to parse"),
                new Parameter("format", "string", "Date format pattern (optional)")
            )
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.date.format",
            "system.date",
            "Format a date object as a string",
            Arrays.asList(
                new Parameter("date", "date", "Date to format"),
                new Parameter("format", "string", "Format pattern (optional)")
            )
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.date.addDays",
            "system.date",
            "Add days to a date",
            Arrays.asList(
                new Parameter("date", "date", "Starting date"),
                new Parameter("days", "int", "Number of days to add")
            )
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.date.addHours",
            "system.date",
            "Add hours to a date",
            Arrays.asList(
                new Parameter("date", "date", "Starting date"),
                new Parameter("hours", "int", "Number of hours to add")
            )
        ));

        // ===== Network Functions (system.net.*) =====
        catalog.add(new SystemFunctionMetadata(
            "system.net.httpGet",
            "system.net",
            "Perform an HTTP GET request",
            Arrays.asList(
                new Parameter("url", "string", "URL to request"),
                new Parameter("headerValues", "dict", "HTTP headers (optional)")
            )
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.net.httpPost",
            "system.net",
            "Perform an HTTP POST request (WRITE OPERATION)",
            Arrays.asList(
                new Parameter("url", "string", "URL to post to"),
                new Parameter("data", "string", "Data to post"),
                new Parameter("headerValues", "dict", "HTTP headers (optional)")
            )
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.net.sendEmail",
            "system.net",
            "Send an email (WRITE OPERATION)",
            Arrays.asList(
                new Parameter("smtp", "string", "SMTP profile name"),
                new Parameter("to", "list", "Recipient email addresses"),
                new Parameter("subject", "string", "Email subject"),
                new Parameter("body", "string", "Email body")
            )
        ));

        // ===== Dataset Functions (system.dataset.*) =====
        catalog.add(new SystemFunctionMetadata(
            "system.dataset.toPyDataSet",
            "system.dataset",
            "Convert a dataset to Python dataset format",
            Arrays.asList(new Parameter("dataset", "dataset", "Dataset to convert"))
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.dataset.toDataSet",
            "system.dataset",
            "Convert data to a dataset",
            Arrays.asList(
                new Parameter("headers", "list", "Column headers"),
                new Parameter("data", "list", "Row data")
            )
        ));

        catalog.add(new SystemFunctionMetadata(
            "system.dataset.exportCSV",
            "system.dataset",
            "Export dataset to CSV string",
            Arrays.asList(
                new Parameter("dataset", "dataset", "Dataset to export"),
                new Parameter("showHeaders", "boolean", "Include headers (optional)")
            )
        ));

        return catalog;
    }

    /**
     * Metadata for a system function.
     */
    static class SystemFunctionMetadata {
        String name;
        String category;
        String description;
        List<Parameter> parameters;

        SystemFunctionMetadata(String name, String category, String description, List<Parameter> parameters) {
            this.name = name;
            this.category = category;
            this.description = description;
            this.parameters = parameters;
        }

        boolean matchesQuery(String query) {
            String lowerQuery = query.toLowerCase();
            return name.toLowerCase().contains(lowerQuery) ||
                   category.toLowerCase().contains(lowerQuery) ||
                   description.toLowerCase().contains(lowerQuery);
        }

        JsonArray parametersToJson() {
            JsonArray arr = new JsonArray();
            for (Parameter param : parameters) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", param.name);
                obj.addProperty("type", param.type);
                obj.addProperty("description", param.description);
                arr.add(obj);
            }
            return arr;
        }
    }

    /**
     * Parameter metadata.
     */
    static class Parameter {
        String name;
        String type;
        String description;

        Parameter(String name, String type, String description) {
            this.name = name;
            this.type = type;
            this.description = description;
        }
    }
}
