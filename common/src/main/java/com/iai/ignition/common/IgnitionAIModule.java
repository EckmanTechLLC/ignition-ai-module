package com.iai.ignition.common;

import com.inductiveautomation.perspective.common.api.BrowserResource;
import java.util.Set;

/**
 * Central location for module and component metadata.
 */
public class IgnitionAIModule {

    public static final String MODULE_ID = "com.iai.ignition.ai";
    public static final String URL_ALIAS = "ignitionai";
    public static final String COMPONENT_CATEGORY = "Ignition AI";

    /**
     * Browser resources required for the components to function.
     */
    public static final Set<BrowserResource> BROWSER_RESOURCES = Set.of(
        new BrowserResource(
            "insight-chat-js",
            String.format("/res/%s/js/insight-chat.js", URL_ALIAS),
            BrowserResource.ResourceType.JS
        )
    );
}
