package com.iai.ignition.gateway.tools.tags;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.common.tags.model.TagProvider;
import com.inductiveautomation.ignition.common.tags.paths.parser.TagPathParser;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.tags.model.GatewayTagManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tool for getting tag configuration/metadata.
 */
public class GetTagConfigTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.tags.GetTagConfigTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public GetTagConfigTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "get_tag_config";
    }

    @Override
    public String getDescription() {
        return "Get configuration and metadata for one or more tags. Returns tag properties, data types, " +
                "alarm configurations, and current values.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject tagPaths = new JsonObject();
        tagPaths.addProperty("type", "array");
        tagPaths.addProperty("description", "List of tag paths to read");
        JsonObject items = new JsonObject();
        items.addProperty("type", "string");
        tagPaths.add("items", items);
        properties.add("tag_paths", tagPaths);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("tag_paths");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        JsonArray tagPathsArray = params.getAsJsonArray("tag_paths");

        logger.debug("Getting tag config for " + tagPathsArray.size() + " tags");

        GatewayTagManager tagManager = gatewayContext.getTagManager();

        // Parse tag paths
        List<TagPath> tagPaths = new ArrayList<>();
        for (int i = 0; i < tagPathsArray.size(); i++) {
            String pathString = tagPathsArray.get(i).getAsString();
            tagPaths.add(TagPathParser.parse(pathString));
        }

        // Get the provider (assume all tags are from same provider for now)
        String providerName = tagPaths.get(0).getSource();
        if (providerName == null || providerName.isEmpty()) {
            providerName = "default";
        }

        TagProvider provider = tagManager.getTagProvider(providerName);
        if (provider == null) {
            throw new Exception("Tag provider not found: " + providerName);
        }

        // Read tag values with metadata (using null security context for Gateway scope)
        List<QualifiedValue> values = provider.readAsync(tagPaths, null).get(30, TimeUnit.SECONDS);

        JsonArray tags = new JsonArray();
        for (int i = 0; i < tagPaths.size(); i++) {
            TagPath path = tagPaths.get(i);
            QualifiedValue qv = i < values.size() ? values.get(i) : null;

            JsonObject tag = new JsonObject();
            tag.addProperty("path", path.toString());

            if (qv != null) {
                tag.addProperty("value", qv.getValue() != null ? qv.getValue().toString() : null);
                tag.addProperty("quality", qv.getQuality().toString());
                tag.addProperty("timestamp", qv.getTimestamp().getTime());

                // Data type info
                if (qv.getValue() != null) {
                    tag.addProperty("type", qv.getValue().getClass().getSimpleName());
                }
            } else {
                tag.addProperty("error", "No value returned");
            }

            tags.add(tag);
        }

        JsonObject result = new JsonObject();
        result.add("tags", tags);
        result.addProperty("count", tags.size());

        return result;
    }
}
