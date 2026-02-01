package com.iai.ignition.gateway.tools.filesystem;

import com.iai.ignition.common.tools.IAITool;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Tool for reading and analyzing Vision window binary files.
 * Deserializes Vision windows to extract component information.
 */
public class ReadVisionWindowTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build(ReadVisionWindowTool.class.getName());

    private final GatewayContext context;
    private final String gatewayDataPath;

    public ReadVisionWindowTool(GatewayContext context, String gatewayDataPath) {
        this.context = context;
        this.gatewayDataPath = gatewayDataPath;
    }

    @Override
    public String getName() {
        return "read_vision_window";
    }

    @Override
    public String getDescription() {
        return "Read and analyze a Vision window binary file to see its components and structure. " +
               "Deserializes the .bin file and extracts component types, names, and properties.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject projectName = new JsonObject();
        projectName.addProperty("type", "string");
        projectName.addProperty("description", "The name of the Ignition project");
        properties.add("project_name", projectName);

        JsonObject windowPath = new JsonObject();
        windowPath.addProperty("type", "string");
        windowPath.addProperty("description", "Path to the Vision window (e.g., 'Main Windows/Overview' or 'Historical')");
        properties.add("window_path", windowPath);

        schema.add("properties", properties);

        com.inductiveautomation.ignition.common.gson.JsonArray required =
            new com.inductiveautomation.ignition.common.gson.JsonArray();
        required.add("project_name");
        required.add("window_path");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject input) {
        JsonObject result = new JsonObject();

        try {
            // Extract parameters
            if (!input.has("project_name") || !input.has("window_path")) {
                result.addProperty("error", "Missing required parameters: project_name and window_path");
                return result;
            }

            String projectName = input.get("project_name").getAsString();
            String windowPath = input.get("window_path").getAsString();

            // Build path to window.bin file
            Path projectPath = Paths.get(gatewayDataPath, "projects", projectName);
            Path windowFilePath = projectPath.resolve("com.inductiveautomation.perspective")
                                             .resolve("views")
                                             .resolve(windowPath)
                                             .resolve("window.bin");

            // Check if it's actually a Vision window (different location)
            if (!Files.exists(windowFilePath)) {
                windowFilePath = projectPath.resolve("com.inductiveautomation.vision")
                                           .resolve("windows")
                                           .resolve(windowPath + ".bin");
            }

            if (!Files.exists(windowFilePath)) {
                result.addProperty("error", "Vision window file not found at: " + windowFilePath);
                result.addProperty("note", "Make sure the window_path is correct (e.g., 'Main Windows/Overview')");
                return result;
            }

            // Read and deserialize the binary file
            byte[] fileBytes = Files.readAllBytes(windowFilePath);

            try (ByteArrayInputStream bais = new ByteArrayInputStream(fileBytes);
                 ObjectInputStream ois = new ObjectInputStream(bais)) {

                Object windowObject = ois.readObject();

                // Analyze the deserialized object
                String windowInfo = analyzeVisionWindow(windowObject);

                result.addProperty("success", true);
                result.addProperty("window_path", windowPath);
                result.addProperty("file_size_bytes", fileBytes.length);
                result.addProperty("window_class", windowObject.getClass().getName());
                result.addProperty("components", windowInfo);

            } catch (Exception e) {
                logger.error("Error deserializing Vision window", e);
                result.addProperty("error", "Failed to deserialize window: " + e.getMessage());
                result.addProperty("note", "The window file may be corrupted or use an incompatible format");
            }

        } catch (Exception e) {
            logger.error("Error reading Vision window", e);
            result.addProperty("error", e.getMessage());
        }

        return result;
    }

    /**
     * Analyze a deserialized Vision window object using reflection.
     * Attempts to extract component information without depending on specific Vision classes.
     */
    private String analyzeVisionWindow(Object windowObj) {
        StringBuilder info = new StringBuilder();

        try {
            // Use reflection to explore the window object
            Class<?> windowClass = windowObj.getClass();
            info.append("Window Type: ").append(windowClass.getSimpleName()).append("\n\n");

            // Try to find and list components
            // Vision windows typically have a root container with child components
            info.append("Attempting to extract component information...\n");

            // Look for common methods that might give us component access
            try {
                var getRootContainerMethod = windowClass.getMethod("getRootContainer");
                Object rootContainer = getRootContainerMethod.invoke(windowObj);

                if (rootContainer != null) {
                    info.append("\nRoot Container: ").append(rootContainer.getClass().getSimpleName()).append("\n");
                    info.append(analyzeContainer(rootContainer, 0));
                }
            } catch (NoSuchMethodException e) {
                // Try alternative methods
                try {
                    var getComponentMethod = windowClass.getMethod("getComponent");
                    Object component = getComponentMethod.invoke(windowObj);

                    if (component != null) {
                        info.append("\nComponent: ").append(component.getClass().getSimpleName()).append("\n");
                        info.append(analyzeContainer(component, 0));
                    }
                } catch (NoSuchMethodException e2) {
                    info.append("\nUnable to access components through standard methods.\n");
                    info.append("Window structure may require manual inspection.\n");
                }
            }

        } catch (Exception e) {
            logger.warn("Error analyzing window structure", e);
            info.append("\nError analyzing structure: ").append(e.getMessage()).append("\n");
        }

        return info.toString();
    }

    /**
     * Recursively analyze a container component to list its children.
     */
    private String analyzeContainer(Object container, int depth) {
        StringBuilder info = new StringBuilder();
        String indent = "  ".repeat(depth);

        try {
            Class<?> containerClass = container.getClass();

            // Try to get component count
            try {
                var getComponentCountMethod = containerClass.getMethod("getComponentCount");
                int count = (Integer) getComponentCountMethod.invoke(container);

                if (count > 0) {
                    var getComponentMethod = containerClass.getMethod("getComponent", int.class);

                    for (int i = 0; i < count && i < 50; i++) { // Limit to first 50 components
                        Object child = getComponentMethod.invoke(container, i);

                        if (child != null) {
                            String componentName = getComponentName(child);
                            String componentType = child.getClass().getSimpleName();

                            info.append(indent).append("- ").append(componentType);
                            if (componentName != null && !componentName.isEmpty()) {
                                info.append(" (").append(componentName).append(")");
                            }
                            info.append("\n");

                            // Recursively analyze if it's a container
                            if (isContainer(child)) {
                                info.append(analyzeContainer(child, depth + 1));
                            }
                        }
                    }

                    if (count > 50) {
                        info.append(indent).append("... and ").append(count - 50).append(" more components\n");
                    }
                }
            } catch (NoSuchMethodException e) {
                // Not a container with standard component access
            }

        } catch (Exception e) {
            logger.debug("Error analyzing container at depth " + depth, e);
        }

        return info.toString();
    }

    /**
     * Try to get the name of a component.
     */
    private String getComponentName(Object component) {
        try {
            var getNameMethod = component.getClass().getMethod("getName");
            Object name = getNameMethod.invoke(component);
            return name != null ? name.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if an object is likely a container (has child components).
     */
    private boolean isContainer(Object obj) {
        try {
            obj.getClass().getMethod("getComponentCount");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
