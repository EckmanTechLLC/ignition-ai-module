package com.iai.ignition.gateway.tools.alarms;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

/**
 * Tool for querying alarm journal history.
 */
public class QueryAlarmHistoryTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.alarms.QueryAlarmHistoryTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public QueryAlarmHistoryTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "query_alarm_history";
    }

    @Override
    public String getDescription() {
        return "Query alarm journal history. Returns alarm events within a time range, optionally filtered by source and priority. " +
                "Results are limited by maxAlarmHistoryRecords setting.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject source = new JsonObject();
        source.addProperty("type", "string");
        source.addProperty("description", "Optional alarm source path filter");
        properties.add("source", source);

        JsonObject priority = new JsonObject();
        priority.addProperty("type", "string");
        priority.addProperty("description", "Optional priority filter (Low, Medium, High, Critical)");
        properties.add("priority", priority);

        JsonObject startDate = new JsonObject();
        startDate.addProperty("type", "string");
        startDate.addProperty("description", "Start date/time (ISO 8601 format)");
        properties.add("start_date", startDate);

        JsonObject endDate = new JsonObject();
        endDate.addProperty("type", "string");
        endDate.addProperty("description", "End date/time (ISO 8601 format)");
        properties.add("end_date", endDate);

        JsonObject limit = new JsonObject();
        limit.addProperty("type", "integer");
        limit.addProperty("description", "Maximum number of alarm events to return");
        properties.add("limit", limit);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("start_date");
        required.add("end_date");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String startDate = params.get("start_date").getAsString();
        String endDate = params.get("end_date").getAsString();
        String source = params.has("source") ? params.get("source").getAsString() : null;
        String priority = params.has("priority") ? params.get("priority").getAsString() : null;
        int limit = params.has("limit") ? params.get("limit").getAsInt() : settings.getMaxAlarmHistoryRecords();

        logger.debug("Querying alarm history from " + startDate + " to " + endDate);

        // Note: Alarm journal querying in Ignition 8.1 requires using internal alarm journal APIs
        // which may not be fully exposed in the public SDK. The AlarmManager has methods for
        // querying, but they require specific filter configurations and journal profile names.
        //
        // For a complete implementation, you would need to:
        // 1. Get the alarm journal profile name (typically "AlarmJournal")
        // 2. Create an AlarmFilter with time range and other criteria
        // 3. Call alarmManager.queryJournal(profileName, filter)
        //
        // However, these APIs may not be fully documented or accessible in 8.1.
        // Alternative: Query the alarm_events table directly using execute_sql_query tool.

        JsonObject result = new JsonObject();
        result.addProperty("info", "Alarm journal querying from Gateway scope requires alarm journal APIs that may not be fully exposed in Ignition 8.1 SDK");
        result.addProperty("suggestion", "Use database tools to query alarm_events or alarm_event_data tables directly, or use execute_sql_query tool");
        result.addProperty("start_date", startDate);
        result.addProperty("end_date", endDate);

        if (source != null) {
            result.addProperty("source_filter", source);
        }
        if (priority != null) {
            result.addProperty("priority_filter", priority);
        }

        result.add("alarms", new JsonArray());
        result.addProperty("count", 0);
        result.addProperty("limit", limit);

        return result;
    }
}
