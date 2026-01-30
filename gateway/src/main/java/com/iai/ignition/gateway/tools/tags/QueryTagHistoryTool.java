package com.iai.ignition.gateway.tools.tags;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

/**
 * Tool for querying tag history.
 */
public class QueryTagHistoryTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.tags.QueryTagHistoryTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public QueryTagHistoryTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "query_tag_history";
    }

    @Override
    public String getDescription() {
        return "Query historical tag data. Returns tag values over a time range with optional aggregation. " +
                "Results are limited by maxTagHistoryRecords setting.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject tagPaths = new JsonObject();
        tagPaths.addProperty("type", "array");
        tagPaths.addProperty("description", "List of tag paths to query");
        JsonObject items = new JsonObject();
        items.addProperty("type", "string");
        tagPaths.add("items", items);
        properties.add("tag_paths", tagPaths);

        JsonObject startDate = new JsonObject();
        startDate.addProperty("type", "string");
        startDate.addProperty("description", "Start date/time (ISO 8601 format)");
        properties.add("start_date", startDate);

        JsonObject endDate = new JsonObject();
        endDate.addProperty("type", "string");
        endDate.addProperty("description", "End date/time (ISO 8601 format)");
        properties.add("end_date", endDate);

        JsonObject returnFormat = new JsonObject();
        returnFormat.addProperty("type", "string");
        returnFormat.addProperty("description", "Format: 'wide' or 'tall' (default: tall)");
        returnFormat.addProperty("default", "tall");
        properties.add("return_format", returnFormat);

        JsonObject aggregateMode = new JsonObject();
        aggregateMode.addProperty("type", "string");
        aggregateMode.addProperty("description", "Aggregation: 'Average', 'MinMax', 'LastValue', etc.");
        properties.add("aggregate_mode", aggregateMode);

        JsonObject returnSize = new JsonObject();
        returnSize.addProperty("type", "integer");
        returnSize.addProperty("description", "Maximum number of records to return");
        properties.add("return_size", returnSize);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("tag_paths");
        required.add("start_date");
        required.add("end_date");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        JsonArray tagPathsArray = params.getAsJsonArray("tag_paths");
        String startDate = params.get("start_date").getAsString();
        String endDate = params.get("end_date").getAsString();

        logger.debug("Querying tag history for " + tagPathsArray.size() + " tags from " + startDate + " to " + endDate);

        // Note: Tag history querying from Gateway scope in Ignition 8.1 requires direct database
        // access to the historian tables or using internal APIs not exposed in the public SDK.
        // For a production implementation, options include:
        // 1. Direct SQL queries to the tag_sqlt_data_* tables
        // 2. Using scripting gateway context if available
        // 3. Using internal/undocumented APIs (not recommended)
        //
        // For now, return a message explaining this limitation.

        JsonObject result = new JsonObject();
        result.addProperty("info", "Tag history querying from Gateway scope requires direct database access or internal APIs not exposed in Ignition 8.1 SDK");
        result.addProperty("suggestion", "For tag history, use database tools to query historian tables directly (tag_sqlt_data_*) or use execute_sql_query tool");
        result.addProperty("start_date", startDate);
        result.addProperty("end_date", endDate);
        result.add("tag_paths", tagPathsArray);
        result.add("data", new JsonArray());
        result.addProperty("count", 0);

        return result;
    }
}
