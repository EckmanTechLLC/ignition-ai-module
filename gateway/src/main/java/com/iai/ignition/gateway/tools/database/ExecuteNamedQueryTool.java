package com.iai.ignition.gateway.tools.database;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

/**
 * Tool for executing a named query.
 */
public class ExecuteNamedQueryTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.database.ExecuteNamedQueryTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public ExecuteNamedQueryTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "execute_named_query";
    }

    @Override
    public String getDescription() {
        return "Execute a named query with parameters. Returns query results. Note: This requires Ignition's " +
                "named query execution API which may not be directly available in Gateway context.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject queryName = new JsonObject();
        queryName.addProperty("type", "string");
        queryName.addProperty("description", "The named query path (e.g., 'Folder/QueryName')");
        properties.add("query_name", queryName);

        JsonObject queryParams = new JsonObject();
        queryParams.addProperty("type", "object");
        queryParams.addProperty("description", "Query parameters as key-value pairs");
        properties.add("params", queryParams);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("query_name");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String queryName = params.get("query_name").getAsString();

        logger.debug("Executing named query: " + queryName);

        // Note: Named query execution from Gateway scope in Ignition 8.1 has limitations:
        // 1. Named queries are project-scoped, requiring a project context
        // 2. The NamedQueryManager requires project name to be specified
        // 3. Gateway scope may not have access to all project resources
        //
        // For Gateway-side execution, options include:
        // 1. Read the named query .sql file from the project resources
        // 2. Extract the SQL and parameters
        // 3. Execute using execute_sql_query tool or direct database connection
        //
        // Alternative: Provide project name and use project-scoped APIs if available,
        // or simply read and parse the named query files from the filesystem.

        JsonObject result = new JsonObject();
        result.addProperty("query_name", queryName);
        result.addProperty("info", "Named query execution from Gateway scope requires project context not readily available in Ignition 8.1 SDK");
        result.addProperty("suggestion", "Use read_named_query tool to get the SQL, then use execute_sql_query tool to run it, or provide project context");
        result.add("rows", new JsonArray());
        result.addProperty("count", 0);

        return result;
    }
}
