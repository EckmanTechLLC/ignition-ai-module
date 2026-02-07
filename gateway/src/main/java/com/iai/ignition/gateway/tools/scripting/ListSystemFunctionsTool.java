package com.iai.ignition.gateway.tools.scripting;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Tool for listing available Ignition system.* scripting functions.
 * Returns comprehensive catalog with descriptions, parameters, and categories.
 */
public class ListSystemFunctionsTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.scripting.ListSystemFunctionsTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;
    private final ScriptExecutor scriptExecutor;

    // System function catalog (built dynamically via reflection)
    private static List<SystemFunctionMetadata> FUNCTION_CATALOG = null;

    public ListSystemFunctionsTool(GatewayContext gatewayContext, IAISettings settings, ScriptExecutor scriptExecutor) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
        this.scriptExecutor = scriptExecutor;
    }

    @Override
    public String getName() {
        return "list_system_functions";
    }

    @Override
    public String getDescription() {
        return "List available Ignition system.* scripting functions that can be executed. " +
               "Returns function names, descriptions, parameters, categories, and current safety mode. " +
               "Supports optional search query to filter functions by name or category.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        // Optional search query
        JsonObject query = new JsonObject();
        query.addProperty("type", "string");
        query.addProperty("description", "Optional search query to filter functions (e.g., 'tag', 'database', 'alarm')");
        properties.add("query", query);

        // Optional category filter
        JsonObject category = new JsonObject();
        category.addProperty("type", "string");
        category.addProperty("description", "Optional category filter (e.g., 'system.tag', 'system.db', 'system.alarm')");
        properties.add("category", category);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        schema.add("required", required); // No required parameters

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String mode = settings.getSystemFunctionMode();
        String searchQuery = params.has("query") ? params.get("query").getAsString() : null;
        String categoryFilter = params.has("category") ? params.get("category").getAsString() : null;

        logger.debug("Listing system functions - mode: " + mode + ", query: " + searchQuery + ", category: " + categoryFilter);

        // Build catalog on first use (lazy initialization)
        if (FUNCTION_CATALOG == null) {
            synchronized (ListSystemFunctionsTool.class) {
                if (FUNCTION_CATALOG == null) {
                    FUNCTION_CATALOG = buildDynamicCatalog();
                }
            }
        }

        // Filter functions based on mode and search criteria
        JsonArray functions = new JsonArray();
        for (SystemFunctionMetadata func : FUNCTION_CATALOG) {
            // Filter by mode
            if (mode.equals("READ_ONLY") && !scriptExecutor.isReadOnlyWhitelisted(func.name)) {
                continue; // Skip non-whitelisted functions in READ_ONLY mode
            }

            // Filter by search query
            if (searchQuery != null && !func.matchesQuery(searchQuery)) {
                continue;
            }

            // Filter by category
            if (categoryFilter != null && !func.category.equals(categoryFilter)) {
                continue;
            }

            // Add function to results
            JsonObject funcJson = new JsonObject();
            funcJson.addProperty("name", func.name);
            funcJson.addProperty("description", func.description);
            funcJson.addProperty("category", func.category);
            funcJson.addProperty("readOnly", scriptExecutor.isReadOnlyWhitelisted(func.name));
            funcJson.add("parameters", func.parametersToJson());

            functions.add(funcJson);
        }

        JsonObject result = new JsonObject();
        result.addProperty("mode", mode);
        result.addProperty("count", functions.size());
        result.add("functions", functions);

        // Add mode explanation
        if (mode.equals("READ_ONLY")) {
            result.addProperty("message", "READ_ONLY mode: Only whitelisted read-only functions available");
        } else if (mode.equals("UNRESTRICTED")) {
            result.addProperty("message", "UNRESTRICTED mode: All system.* functions available (testing only)");
        } else {
            result.addProperty("message", "System function execution is disabled");
        }


        return result;
    }

    /**
     * Build comprehensive catalog of system functions via reflection.
     * Discovers all system.* functions by introspecting Ignition's Java utility classes.
     */
    private static List<SystemFunctionMetadata> buildDynamicCatalog() {
        List<SystemFunctionMetadata> catalog = new ArrayList<>();

        // Map of system module prefix to Java utility class name
        Map<String, String> systemModules = new LinkedHashMap<>();
        systemModules.put("system.tag", "com.inductiveautomation.ignition.common.script.builtin.AbstractTagUtilities");
        systemModules.put("system.db", "com.inductiveautomation.ignition.common.script.builtin.AbstractDBUtilities");
        systemModules.put("system.alarm", "com.inductiveautomation.ignition.common.script.builtin.AbstractAlarmUtilities");
        systemModules.put("system.date", "com.inductiveautomation.ignition.common.script.builtin.AbstractDateUtilities");
        systemModules.put("system.util", "com.inductiveautomation.ignition.common.script.builtin.AbstractSystemUtilities");
        systemModules.put("system.net", "com.inductiveautomation.ignition.common.script.builtin.AbstractNetUtilities");
        systemModules.put("system.dataset", "com.inductiveautomation.ignition.common.script.builtin.DatasetUtilities");
        systemModules.put("system.file", "com.inductiveautomation.ignition.common.script.builtin.AbstractFileUtilities");
        systemModules.put("system.security", "com.inductiveautomation.ignition.common.script.builtin.AbstractSecurityUtilities");
        systemModules.put("system.user", "com.inductiveautomation.ignition.gateway.script.GatewayUserUtilities");
        systemModules.put("system.device", "com.inductiveautomation.ignition.gateway.script.GatewayDeviceUtilities");
        systemModules.put("system.opc", "com.inductiveautomation.ignition.gateway.script.GatewayOPCUtilities");
        systemModules.put("system.project", "com.inductiveautomation.ignition.gateway.script.GatewayProjectUtilities");

        // Introspect each module
        for (Map.Entry<String, String> entry : systemModules.entrySet()) {
            String modulePrefix = entry.getKey();
            String className = entry.getValue();

            try {
                Class<?> clazz = Class.forName(className);
                catalog.addAll(extractFunctionsFromClass(clazz, modulePrefix));
            } catch (ClassNotFoundException e) {
                logger.warn("System module class not found: " + className + " (skipping)");
            } catch (Exception e) {
                logger.error("Error introspecting module: " + modulePrefix, e);
            }
        }

        return catalog;
    }

    /**
     * Extract all functions from a utility class via reflection.
     */
    private static List<SystemFunctionMetadata> extractFunctionsFromClass(Class<?> clazz, String modulePrefix) {
        List<SystemFunctionMetadata> functions = new ArrayList<>();

        // Get all public methods
        for (Method method : clazz.getMethods()) {
            try {
                // Check for @KeywordArgs annotation (indicates real function, not artifact)
                Annotation keywordArgs = null;
                for (Annotation ann : method.getAnnotations()) {
                    if (ann.annotationType().getSimpleName().equals("KeywordArgs")) {
                        keywordArgs = ann;
                        break;
                    }
                }

                if (keywordArgs == null) {
                    continue; // Skip artifacts without @KeywordArgs
                }

                // Extract function name
                String functionName = modulePrefix + "." + method.getName();

                // Extract parameters from @KeywordArgs annotation
                List<Parameter> parameters = extractParameters(keywordArgs);

                // Extract description from resource bundle
                String description = extractDescription(clazz, method.getName());

                // Create metadata
                SystemFunctionMetadata metadata = new SystemFunctionMetadata(
                    functionName,
                    modulePrefix,
                    description,
                    parameters
                );

                functions.add(metadata);

            } catch (Exception e) {
                logger.warn("Error extracting metadata for method: " + method.getName(), e);
            }
        }

        return functions;
    }

    /**
     * Extract parameter list from @KeywordArgs annotation.
     */
    private static List<Parameter> extractParameters(Annotation keywordArgs) {
        List<Parameter> parameters = new ArrayList<>();

        try {
            // Get annotation values via reflection
            Method namesMethod = keywordArgs.annotationType().getMethod("names");
            Method typesMethod = keywordArgs.annotationType().getMethod("types");

            String[] names = (String[]) namesMethod.invoke(keywordArgs);
            Class<?>[] types = (Class<?>[]) typesMethod.invoke(keywordArgs);

            // Create Parameter objects
            for (int i = 0; i < names.length; i++) {
                String paramName = names[i];
                String paramType = simplifyTypeName(types[i]);
                String paramDesc = paramName; // Default description is parameter name

                parameters.add(new Parameter(paramName, paramType, paramDesc));
            }

        } catch (Exception e) {
            logger.warn("Error extracting parameters from @KeywordArgs", e);
        }

        return parameters;
    }

    /**
     * Extract function description from resource bundle.
     */
    private static String extractDescription(Class<?> clazz, String methodName) {
        try {
            // Find @ScriptFunction annotation to get docBundlePrefix
            for (Annotation ann : clazz.getAnnotations()) {
                if (ann.annotationType().getSimpleName().equals("ScriptFunction")) {
                    Method prefixMethod = ann.annotationType().getMethod("docBundlePrefix");
                    String bundlePrefix = (String) prefixMethod.invoke(ann);

                    // Load resource bundle
                    String bundleName = "com.inductiveautomation.ignition.common.script.builtin." + bundlePrefix;
                    ResourceBundle bundle = ResourceBundle.getBundle(bundleName);

                    // Get function description
                    String descKey = methodName + ".desc";
                    if (bundle.containsKey(descKey)) {
                        return bundle.getString(descKey);
                    }
                }
            }
        } catch (Exception e) {
            // Resource bundle not found or key missing - not critical
        }

        // Fallback: generate description from method name
        return "Execute " + methodName + " function";
    }

    /**
     * Simplify Java type names to friendly type strings.
     */
    private static String simplifyTypeName(Class<?> type) {
        String name = type.getSimpleName().toLowerCase();

        if (name.contains("string")) return "string";
        if (name.contains("int") || name.contains("long")) return "int";
        if (name.contains("double") || name.contains("float")) return "number";
        if (name.contains("boolean")) return "boolean";
        if (name.contains("date")) return "date";
        if (name.contains("list")) return "list";
        if (name.contains("map") || name.contains("dict")) return "dict";
        if (name.contains("dataset")) return "dataset";

        return "object"; // Fallback
    }

    /**
     * Metadata for a system function.
     */
    static class SystemFunctionMetadata {
        String name;
        String category;
        String description;
        List<Parameter> parameters;

        SystemFunctionMetadata(String name, String category, String description, List<Parameter> parameters) {
            this.name = name;
            this.category = category;
            this.description = description;
            this.parameters = parameters;
        }

        boolean matchesQuery(String query) {
            String lowerQuery = query.toLowerCase();
            return name.toLowerCase().contains(lowerQuery) ||
                   category.toLowerCase().contains(lowerQuery) ||
                   description.toLowerCase().contains(lowerQuery);
        }

        JsonArray parametersToJson() {
            JsonArray arr = new JsonArray();
            for (Parameter param : parameters) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", param.name);
                obj.addProperty("type", param.type);
                obj.addProperty("description", param.description);
                arr.add(obj);
            }
            return arr;
        }
    }

    /**
     * Parameter metadata.
     */
    static class Parameter {
        String name;
        String type;
        String description;

        Parameter(String name, String type, String description) {
            this.name = name;
            this.type = type;
            this.description = description;
        }
    }
}
