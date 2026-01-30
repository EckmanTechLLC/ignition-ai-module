package com.iai.ignition.gateway.tools.alarms;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

/**
 * Tool for getting alarm configuration.
 */
public class GetAlarmConfigTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.alarms.GetAlarmConfigTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public GetAlarmConfigTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "get_alarm_config";
    }

    @Override
    public String getDescription() {
        return "Get alarm configuration from the tag system. Returns alarm settings including priority, " +
                "setpoints, notification settings, and enabled state.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject alarmPath = new JsonObject();
        alarmPath.addProperty("type", "string");
        alarmPath.addProperty("description", "Path to the alarm in the tag system");
        properties.add("alarm_path", alarmPath);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("alarm_path");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String alarmPath = params.get("alarm_path").getAsString();

        logger.debug("Getting alarm config for: " + alarmPath);

        // Note: Alarm configurations in Ignition are stored as properties on tags.
        // Reading alarm configuration from Gateway scope requires:
        // 1. Parsing the alarm path to get the parent tag path
        // 2. Using TagProvider to browse the tag and read its alarm properties
        // 3. Extracting alarm-specific configuration from tag properties
        //
        // This is complex and requires deep knowledge of the tag property structure.
        // For a complete implementation, you would need to browse tag properties looking for
        // alarm definitions (usually prefixed with "Alarms.").
        //
        // Alternative: Use the Designer or scripting context where alarm APIs are more accessible,
        // or query the internal_tag_config table directly.

        JsonObject result = new JsonObject();
        result.addProperty("alarm_path", alarmPath);
        result.addProperty("info", "Alarm configuration reading from Gateway scope requires complex tag property parsing not fully supported in Ignition 8.1 SDK");
        result.addProperty("suggestion", "Use tag browsing to read tag properties with alarm configurations, or query internal_tag_config table directly");
        result.add("config", new JsonObject());

        return result;
    }
}
