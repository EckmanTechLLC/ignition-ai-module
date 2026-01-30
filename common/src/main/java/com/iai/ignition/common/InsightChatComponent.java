package com.iai.ignition.common;

import com.inductiveautomation.ignition.common.jsonschema.JsonSchema;
import com.inductiveautomation.perspective.common.api.ComponentDescriptor;
import com.inductiveautomation.perspective.common.api.ComponentDescriptorImpl;

/**
 * Describes the Insight Chat component to the Java registry.
 */
public class InsightChatComponent {

    public static final String COMPONENT_ID = "com.iai.ignition.insight-chat";

    /**
     * JSON schema for component properties.
     */
    public static final JsonSchema SCHEMA =
        JsonSchema.parse(InsightChatComponent.class.getResourceAsStream("/insight-chat.props.json"));

    /**
     * Component descriptor for registration.
     */
    public static final ComponentDescriptor DESCRIPTOR = ComponentDescriptorImpl.ComponentBuilder.newBuilder()
        .setPaletteCategory(IgnitionAIModule.COMPONENT_CATEGORY)
        .setId(COMPONENT_ID)
        .setModuleId(IgnitionAIModule.MODULE_ID)
        .setSchema(SCHEMA)
        .setName("Insight Chat")
        .addPaletteEntry("", "Insight Chat", "AI-powered chat interface for understanding Ignition systems.", null, null)
        .setDefaultMetaName("insightChat")
        .setResources(IgnitionAIModule.BROWSER_RESOURCES)
        .build();
}
