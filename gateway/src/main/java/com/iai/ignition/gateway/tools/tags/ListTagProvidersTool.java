package com.iai.ignition.gateway.tools.tags;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.tags.model.TagProvider;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.tags.model.GatewayTagManager;

import java.util.Collection;

/**
 * Tool for listing all tag providers in the Gateway.
 * Returns provider names like [default], [System], [edge], etc.
 */
public class ListTagProvidersTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.tags.ListTagProvidersTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public ListTagProvidersTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "list_tag_providers";
    }

    @Override
    public String getDescription() {
        return "List all tag providers available in the Ignition Gateway. " +
                "Returns provider names (e.g., [default], [System], [edge]) and their status.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        // No parameters needed - just list all providers
        JsonObject properties = new JsonObject();
        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        logger.debug("Listing tag providers");

        GatewayTagManager tagManager = gatewayContext.getTagManager();
        Collection<TagProvider> providers = tagManager.getTagProviders();

        if (providers == null || providers.isEmpty()) {
            throw new Exception("No tag providers available");
        }

        JsonArray providerArray = new JsonArray();
        for (TagProvider provider : providers) {
            JsonObject providerObj = new JsonObject();
            providerObj.addProperty("name", provider.getName());

            // Format as [providerName] for display
            providerObj.addProperty("displayName", "[" + provider.getName() + "]");

            providerArray.add(providerObj);
        }

        JsonObject result = new JsonObject();
        result.add("providers", providerArray);
        result.addProperty("count", providers.size());

        logger.debug("Found " + providers.size() + " tag provider(s)");

        return result;
    }
}
