package com.iai.ignition.gateway.tools.tags;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;
import com.inductiveautomation.ignition.common.sqltags.model.types.DataType;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.common.tags.model.TagProvider;
import com.inductiveautomation.ignition.common.tags.paths.parser.TagPathParser;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.tags.model.GatewayTagManager;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Tool for listing tags in the tag system.
 * Uses Gateway tag browsing API.
 */
public class ListTagsTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.tags.ListTagsTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public ListTagsTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "list_tags";
    }

    @Override
    public String getDescription() {
        return "List tags in the Ignition tag system. Browse tag structure under a specified path. " +
                "Returns tag names, types, and paths.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject path = new JsonObject();
        path.addProperty("type", "string");
        path.addProperty("description", "Tag path to browse (default: root, use '[default]' for default provider)");
        path.addProperty("default", "[default]");
        properties.add("path", path);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String pathString = params.has("path") ? params.get("path").getAsString() : "[default]";

        logger.debug("Listing tags for path: " + pathString);

        GatewayTagManager tagManager = gatewayContext.getTagManager();
        TagPath tagPath = TagPathParser.parse(pathString);

        // Get the provider from the path (defaults to first available)
        String providerName = tagPath.getSource();
        if (providerName == null || providerName.isEmpty()) {
            Collection<TagProvider> providers = tagManager.getTagProviders();
            if (providers.isEmpty()) {
                throw new Exception("No tag providers available");
            }
            providerName = providers.iterator().next().getName();
            tagPath = TagPathParser.parse("[" + providerName + "]" + tagPath.toStringPartial());
        }

        TagProvider provider = tagManager.getTagProvider(providerName);
        if (provider == null) {
            throw new Exception("Tag provider not found: " + providerName);
        }

        // Browse tags at this path
        JsonArray tags = new JsonArray();
        try {
            var results = provider.browseAsync(tagPath, null).get(30, TimeUnit.SECONDS);

            if (results.getResultQuality().isNotGood()) {
                throw new Exception("Browse failed with quality: " + results.getResultQuality().toString());
            }

            for (var node : results.getResults()) {
                JsonObject tag = new JsonObject();
                tag.addProperty("name", node.getName());
                tag.addProperty("path", tagPath.getChildPath(node.getName()).toString());
                tag.addProperty("hasChildren", node.hasChildren());

                if (node.getDataType() != null) {
                    tag.addProperty("dataType", node.getDataType().toString());
                }

                // Get current value if this is a tag (not a folder)
                if (!node.hasChildren() || node.getDataType() != DataType.Document) {
                    QualifiedValue value = node.getCurrentValue();
                    if (value != null && value.getQuality().isGood()) {
                        tag.addProperty("value", value.getValue().toString());
                        tag.addProperty("quality", value.getQuality().toString());
                    }
                }

                tags.add(tag);
            }
        } catch (Exception e) {
            logger.error("Error browsing tags at path: " + pathString, e);
            throw new Exception("Failed to browse tags: " + e.getMessage());
        }

        JsonObject result = new JsonObject();
        result.addProperty("path", pathString);
        result.addProperty("provider", providerName);
        result.add("tags", tags);
        result.addProperty("count", tags.size());

        return result;
    }
}
