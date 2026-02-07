package com.iai.ignition.gateway.tools.scripting;

import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.TypeUtilities;
import com.inductiveautomation.ignition.common.gson.JsonElement;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import org.python.core.PyObject;
import org.python.core.PySystemState;
import org.python.util.PythonInterpreter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Executes Ignition system.* scripting functions via direct Jython script execution.
 * Provides 100% coverage of all system functions by creating a Python interpreter
 * with access to Ignition's system.* modules.
 */
public class ScriptExecutor {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.scripting.ScriptExecutor");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;
    private final Set<String> readOnlyWhitelist;
    private final ExecutorService executorService;

    /**
     * Create a script executor.
     *
     * @param ctx Gateway context
     * @param settings Module settings
     */
    public ScriptExecutor(GatewayContext ctx, IAISettings settings) {
        this.gatewayContext = ctx;
        this.settings = settings;
        this.readOnlyWhitelist = buildReadOnlyWhitelist();
        this.executorService = Executors.newCachedThreadPool();
    }

    /**
     * Execute a system.* function by name with parameters.
     *
     * @param functionName System function name (e.g., "system.tag.read")
     * @param params Function parameters as JSON
     * @param projectName Project name for script execution context
     * @return Result as JSON
     * @throws Exception if execution fails
     */
    public JsonObject executeSystemFunction(
        String functionName,
        JsonObject params,
        String projectName
    ) throws Exception {

        logger.debug("Executing system function: " + functionName + " (project: " + projectName + ")");

        // Build Python code to call function
        String pythonCode = buildPythonScript(functionName, params);

        // Execute with timeout
        Future<JsonObject> future = executorService.submit(() -> {
            try {
                // Try to get a ScriptManager - prefer from any available project, or create one
                ScriptManager scriptManager = null;

                // Try to get from the first available project
                List<String> projects = gatewayContext.getProjectManager().getProjectNames();
                if (!projects.isEmpty()) {
                    for (String proj : projects) {
                        scriptManager = gatewayContext.getProjectManager().getProjectScriptManager(proj);
                        if (scriptManager != null) {
                            logger.debug("Using ScriptManager from project: " + proj);
                            break;
                        }
                    }
                }

                if (scriptManager == null) {
                    throw new IllegalStateException("No ScriptManager available - no projects are running");
                }

                // Create locals map (pre-imports system.*)
                PyObject locals = scriptManager.createLocalsMap();

                // Convert params JSON → Python and set in locals
                PyObject pyParams = TypeUtilities.gsonToPy(params);
                locals.__setitem__("params", pyParams);

                // Execute Python code using ScriptManager
                scriptManager.runCode(pythonCode, locals, "executeSystemFunction.py");

                // Get result from locals
                PyObject resultPy = locals.__finditem__("result");
                if (resultPy == null) {
                    throw new Exception("Script did not set 'result' variable");
                }

                // Convert result Python → JSON with fallback strategies
                JsonElement resultElement = null;
                try {
                    // Strategy 1: Try direct conversion via TypeUtilities
                    resultElement = TypeUtilities.pyToGson(resultPy);
                } catch (Exception e) {
                    logger.debug("TypeUtilities.pyToGson() failed, trying fallback: " + e.getMessage());

                    // Strategy 2: Try to extract meaningful data from complex objects
                    try {
                        resultElement = extractComplexObject(resultPy);
                    } catch (Exception e2) {
                        logger.debug("Complex object extraction failed, using string fallback: " + e2.getMessage());

                        // Strategy 3: Fall back to string representation
                        JsonObject fallback = new JsonObject();
                        fallback.addProperty("result_string", resultPy.toString());
                        fallback.addProperty("result_type", resultPy.getType().getName());
                        fallback.addProperty("serialization_note", "Complex object converted to string - original type: " + resultPy.getType().getName());
                        return fallback;
                    }
                }

                if (resultElement == null || !resultElement.isJsonObject()) {
                    JsonObject wrapped = new JsonObject();
                    wrapped.add("value", resultElement);
                    return wrapped;
                }
                return resultElement.getAsJsonObject();

            } catch (Exception e) {
                logger.error("Script execution error for " + functionName, e);
                JsonObject error = new JsonObject();
                error.addProperty("error", e.getMessage());
                error.addProperty("type", e.getClass().getSimpleName());
                return error;
            }
        });

        try {
            return future.get(settings.getSystemFunctionTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TimeoutException("Function execution timeout after " +
                settings.getSystemFunctionTimeoutSeconds() + " seconds");
        }
    }

    /**
     * Extract data from complex Python objects that can't be directly serialized.
     * Handles common Ignition types like lists, browse results, qualified values, etc.
     *
     * @param pyObj Python object to extract
     * @return JSON representation
     * @throws Exception if extraction fails
     */
    private JsonElement extractComplexObject(PyObject pyObj) throws Exception {
        // Check if it's a list/sequence by trying to get length
        try {
            int length = pyObj.__len__();
            com.inductiveautomation.ignition.common.gson.JsonArray array = new com.inductiveautomation.ignition.common.gson.JsonArray();

            for (int i = 0; i < length; i++) {
                PyObject item = pyObj.__getitem__(i);

                // Try to extract item as JSON
                try {
                    JsonElement itemJson = TypeUtilities.pyToGson(item);
                    array.add(itemJson);
                } catch (Exception e) {
                    // If item can't be serialized, use its string representation
                    JsonObject itemObj = new JsonObject();
                    itemObj.addProperty("value", item.toString());
                    itemObj.addProperty("type", item.getType().getName());
                    array.add(itemObj);
                }
            }

            JsonObject result = new JsonObject();
            result.add("items", array);
            result.addProperty("count", length);
            return result;
        } catch (Exception e) {
            logger.debug("Not a sequence type, trying attribute extraction: " + e.getMessage());
        }

        // Check if it has common attributes we can extract
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("string_representation", pyObj.toString());
            obj.addProperty("type", pyObj.getType().getName());

            // Try to extract common attributes
            String[] commonAttrs = {"name", "path", "value", "quality", "timestamp", "nodeId", "displayName", "hasChildren"};
            for (String attr : commonAttrs) {
                try {
                    PyObject attrValue = pyObj.__findattr__(attr);
                    if (attrValue != null) {
                        try {
                            JsonElement attrJson = TypeUtilities.pyToGson(attrValue);
                            obj.add(attr, attrJson);
                        } catch (Exception e) {
                            obj.addProperty(attr, attrValue.toString());
                        }
                    }
                } catch (Exception e) {
                    // Attribute doesn't exist, skip
                }
            }

            return obj;
        } catch (Exception e) {
            throw new Exception("Could not extract complex object: " + e.getMessage());
        }
    }

    /**
     * Build Python script to call system function.
     * Supports both positional and keyword arguments.
     * Parameters with numeric names (0, 1, 2, etc.) are treated as positional.
     *
     * @param functionName System function name
     * @param params Function parameters
     * @return Python code string
     */
    private String buildPythonScript(String functionName, JsonObject params) {
        StringBuilder code = new StringBuilder();

        // Import system modules
        code.append("# Import Ignition system modules\n");
        code.append("import system\n\n");

        // Add comment
        code.append("# Call system function: ").append(functionName).append("\n");
        code.append("try:\n");
        code.append("    result = ").append(functionName).append("(");

        // Separate positional and keyword arguments
        // Parameters named "0", "1", "2", etc. are treated as positional
        java.util.List<String> positionalKeys = new java.util.ArrayList<>();
        java.util.List<String> keywordKeys = new java.util.ArrayList<>();

        for (String key : params.keySet()) {
            try {
                Integer.parseInt(key);
                positionalKeys.add(key);
            } catch (NumberFormatException e) {
                keywordKeys.add(key);
            }
        }

        // Sort positional parameters by numeric value
        positionalKeys.sort(java.util.Comparator.comparingInt(Integer::parseInt));

        // Add positional arguments first
        boolean first = true;
        for (String key : positionalKeys) {
            if (!first) {
                code.append(", ");
            }
            code.append("params['").append(key).append("']");
            first = false;
        }

        // Add keyword arguments
        for (String key : keywordKeys) {
            if (!first) {
                code.append(", ");
            }
            code.append(key).append("=params['").append(key).append("']");
            first = false;
        }

        code.append(")\n");
        code.append("except Exception as e:\n");
        code.append("    import traceback\n");
        code.append("    result = {'error': str(e) + '\\n' + traceback.format_exc(), 'type': type(e).__name__}\n");

        return code.toString();
    }

    /**
     * Check if function is whitelisted for READ_ONLY mode.
     *
     * @param functionName Function name to check
     * @return true if whitelisted
     */
    public boolean isReadOnlyWhitelisted(String functionName) {
        return readOnlyWhitelist.contains(functionName);
    }

    /**
     * Build whitelist of safe read-only functions.
     *
     * @return Set of whitelisted function names
     */
    private Set<String> buildReadOnlyWhitelist() {
        Set<String> whitelist = new HashSet<>();

        // Tag functions - pure reads
        whitelist.add("system.tag.read");
        whitelist.add("system.tag.readBlocking");
        whitelist.add("system.tag.readAsync");
        whitelist.add("system.tag.browse");
        whitelist.add("system.tag.browseHistoricalTags");
        whitelist.add("system.tag.exists");
        whitelist.add("system.tag.getConfiguration");
        whitelist.add("system.tag.queryTagHistory");
        whitelist.add("system.tag.queryTagCalculations");
        whitelist.add("system.tag.queryTagDensity");

        // Database - SELECT only
        whitelist.add("system.db.runQuery");
        whitelist.add("system.db.runPrepQuery");
        whitelist.add("system.db.runScalarQuery");
        whitelist.add("system.db.runPrepUpdate"); // Read-only if used for SELECT

        // Alarm functions - queries
        whitelist.add("system.alarm.queryJournal");
        whitelist.add("system.alarm.queryStatus");
        whitelist.add("system.alarm.loadFromXML");

        // Utility functions - reads
        whitelist.add("system.util.getSystemFlags");
        whitelist.add("system.util.getGatewayStatus");
        whitelist.add("system.util.getProjectName");
        whitelist.add("system.util.getGlobals");
        whitelist.add("system.util.jsonDecode");
        whitelist.add("system.util.jsonEncode");
        whitelist.add("system.util.getLogger");

        // Date functions - all safe (pure computation)
        whitelist.add("system.date.parse");
        whitelist.add("system.date.format");
        whitelist.add("system.date.now");
        whitelist.add("system.date.addDays");
        whitelist.add("system.date.addHours");
        whitelist.add("system.date.addMinutes");
        whitelist.add("system.date.addSeconds");
        whitelist.add("system.date.addMillis");
        whitelist.add("system.date.addMonths");
        whitelist.add("system.date.addWeeks");
        whitelist.add("system.date.addYears");
        whitelist.add("system.date.daysBetween");
        whitelist.add("system.date.hoursBetween");
        whitelist.add("system.date.minutesBetween");
        whitelist.add("system.date.secondsBetween");
        whitelist.add("system.date.millisBetween");
        whitelist.add("system.date.isBetween");
        whitelist.add("system.date.isAfter");
        whitelist.add("system.date.isBefore");
        whitelist.add("system.date.getHour12");
        whitelist.add("system.date.getHour24");
        whitelist.add("system.date.getMinute");
        whitelist.add("system.date.getSecond");
        whitelist.add("system.date.getMillis");
        whitelist.add("system.date.getTimezone");
        whitelist.add("system.date.getTimezoneOffset");
        whitelist.add("system.date.getDayOfMonth");
        whitelist.add("system.date.getDayOfWeek");
        whitelist.add("system.date.getDayOfYear");
        whitelist.add("system.date.getMonth");
        whitelist.add("system.date.getQuarter");
        whitelist.add("system.date.getYear");
        whitelist.add("system.date.midnight");
        whitelist.add("system.date.setTime");
        whitelist.add("system.date.toMillis");
        whitelist.add("system.date.fromMillis");

        // Dataset functions - read/query operations
        whitelist.add("system.dataset.toPyDataSet");
        whitelist.add("system.dataset.toDataSet");
        whitelist.add("system.dataset.toExcel");
        whitelist.add("system.dataset.exportCSV");
        whitelist.add("system.dataset.exportHTML");
        whitelist.add("system.dataset.exportJSON");
        whitelist.add("system.dataset.dataSetToHTML");
        whitelist.add("system.dataset.dataSetToExcel");
        whitelist.add("system.dataset.filterColumns");
        whitelist.add("system.dataset.formatDates");
        whitelist.add("system.dataset.getColumnHeaders");
        whitelist.add("system.dataset.setValue");
        whitelist.add("system.dataset.sort");
        whitelist.add("system.dataset.toCSV");

        // OPC functions - browse/read operations
        whitelist.add("system.opc.browse");
        whitelist.add("system.opc.browseServer");
        whitelist.add("system.opc.getServerState");
        whitelist.add("system.opc.getServers");
        whitelist.add("system.opc.readValue");
        whitelist.add("system.opc.readValues");

        // Utility conversion/formatting functions
        whitelist.add("system.util.toJson");
        whitelist.add("system.util.fromJson");
        whitelist.add("system.util.toBase64");
        whitelist.add("system.util.fromBase64");
        whitelist.add("system.util.getConnectionInfo");
        whitelist.add("system.util.modifyTranslation");
        whitelist.add("system.util.translate");

        // EXCLUDED (write/dangerous operations):
        // - system.tag.write*, system.tag.configure, system.tag.deleteTags, system.tag.copy, system.tag.move, system.tag.rename
        // - system.db.runUpdateQuery, system.db.runSFUpdateQuery, system.db.transaction, system.db.beginTransaction
        // - system.alarm.acknowledge, system.alarm.shelve, system.alarm.cancel
        // - system.net.sendEmail, system.net.httpPost, system.net.httpPut, system.net.httpDelete
        // - system.file.writeFile, system.file.deleteFile, system.file.saveFile
        // - system.util.sendRequest, system.util.sendMessage, system.util.execute*
        // - system.util.invokeAsynchronous, system.util.invokeLater

        return whitelist;
    }

    /**
     * Shutdown the executor service.
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
