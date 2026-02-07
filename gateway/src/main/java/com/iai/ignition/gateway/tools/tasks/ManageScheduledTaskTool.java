package com.iai.ignition.gateway.tools.tasks;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.database.TaskDAO;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

/**
 * Tool for managing scheduled tasks (pause, resume, delete).
 * Allows the AI to control task execution state.
 */
public class ManageScheduledTaskTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.tasks.ManageScheduledTaskTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public ManageScheduledTaskTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "manage_scheduled_task";
    }

    @Override
    public String getDescription() {
        return "Manage a scheduled task by pausing, resuming, or deleting it. " +
               "Use 'pause' to temporarily stop task execution, 'resume' to restart it, or 'delete' to permanently remove the task. " +
               "Requires the task ID which can be obtained from list_scheduled_tasks tool.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        // taskId
        JsonObject taskId = new JsonObject();
        taskId.addProperty("type", "string");
        taskId.addProperty("description", "The ID of the scheduled task to manage (UUID format)");
        properties.add("taskId", taskId);

        // action
        JsonObject action = new JsonObject();
        action.addProperty("type", "string");
        action.addProperty("description", "Action to perform on the task");
        JsonArray actionEnum = new JsonArray();
        actionEnum.add("pause");
        actionEnum.add("resume");
        actionEnum.add("delete");
        action.add("enum", actionEnum);
        properties.add("action", action);

        schema.add("properties", properties);

        // Required fields
        JsonArray required = new JsonArray();
        required.add("taskId");
        required.add("action");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String taskId = params.get("taskId").getAsString();
        String action = params.get("action").getAsString().toLowerCase();

        // Validate action
        if (!action.equals("pause") && !action.equals("resume") && !action.equals("delete")) {
            throw new IllegalArgumentException("Invalid action. Must be 'pause', 'resume', or 'delete'");
        }

        logger.info("Managing scheduled task: taskId=" + taskId + ", action=" + action);

        String dbConnection = settings.getDatabaseConnection();
        if (dbConnection == null || dbConnection.isEmpty()) {
            throw new IllegalStateException("Database connection not configured in module settings");
        }

        boolean success = false;
        String message = "";

        switch (action) {
            case "pause":
                success = TaskDAO.updateTaskEnabled(
                    gatewayContext.getDatasourceManager(),
                    dbConnection,
                    taskId,
                    false
                );
                message = success ? "Task paused successfully. It will no longer execute on schedule." : "Failed to pause task";
                break;

            case "resume":
                success = TaskDAO.updateTaskEnabled(
                    gatewayContext.getDatasourceManager(),
                    dbConnection,
                    taskId,
                    true
                );
                message = success ? "Task resumed successfully. It will execute on its schedule." : "Failed to resume task";
                break;

            case "delete":
                success = TaskDAO.deleteTask(
                    gatewayContext.getDatasourceManager(),
                    dbConnection,
                    taskId
                );
                message = success ? "Task deleted successfully. It has been permanently removed." : "Failed to delete task";
                break;
        }

        if (success) {
            logger.info("Task management successful: " + action + " on task " + taskId);
        } else {
            logger.warn("Task management failed: " + action + " on task " + taskId);
        }

        // Build response
        JsonObject result = new JsonObject();
        result.addProperty("success", success);
        result.addProperty("taskId", taskId);
        result.addProperty("action", action);
        result.addProperty("message", message);

        if (!success) {
            result.addProperty("error", "Operation failed. Task may not exist or database error occurred.");
        }

        return result;
    }
}
