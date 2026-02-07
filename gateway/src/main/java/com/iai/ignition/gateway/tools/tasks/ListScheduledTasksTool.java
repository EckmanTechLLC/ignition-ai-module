package com.iai.ignition.gateway.tools.tasks;

import com.iai.ignition.common.model.ScheduledTask;
import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.database.TaskDAO;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.util.List;

/**
 * Tool for listing scheduled tasks.
 * Allows the AI to view existing scheduled tasks for a user/project.
 */
public class ListScheduledTasksTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.tasks.ListScheduledTasksTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;
    private String currentUserName;
    private String currentProjectName;

    public ListScheduledTasksTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    /**
     * Set the current conversation context.
     * Called by ToolRegistry before execute().
     */
    public void setContext(String userName, String projectName) {
        this.currentUserName = userName;
        this.currentProjectName = projectName;
    }

    @Override
    public String getName() {
        return "list_scheduled_tasks";
    }

    @Override
    public String getDescription() {
        return "List scheduled tasks for a user and project. " +
               "Use this to show the user their existing scheduled tasks or to check if a task already exists. " +
               "Returns task details including ID, description, schedule, next run time, and status.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        // userName and projectName now come from context (no parameters needed)
        schema.add("properties", properties);

        // No required fields
        JsonArray required = new JsonArray();
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        // Use context values (set by ToolRegistry) instead of parameters
        String userName = currentUserName;
        String projectName = currentProjectName;

        // Validate context
        if (projectName == null || projectName.isEmpty()) {
            throw new IllegalStateException("Project name not available from conversation context");
        }

        logger.debug("Listing scheduled tasks: userName=" + userName + ", projectName=" + projectName);

        String dbConnection = settings.getDatabaseConnection();
        if (dbConnection == null || dbConnection.isEmpty()) {
            throw new IllegalStateException("Database connection not configured in module settings");
        }

        // Query tasks from database
        List<ScheduledTask> tasks = TaskDAO.getTasksByUser(
            gatewayContext.getDatasourceManager(),
            dbConnection,
            userName,
            projectName
        );

        logger.debug("Found " + tasks.size() + " scheduled tasks");

        // Build response
        JsonArray tasksArray = new JsonArray();
        int activeCount = 0;
        int pausedCount = 0;

        for (ScheduledTask task : tasks) {
            JsonObject taskObj = new JsonObject();
            taskObj.addProperty("id", task.getId());
            taskObj.addProperty("taskDescription", task.getTaskDescription());
            taskObj.addProperty("prompt", task.getPrompt());
            taskObj.addProperty("cronExpression", task.getCronExpression());
            taskObj.addProperty("nextRunAt", task.getNextRunAt());
            taskObj.addProperty("nextRunAtFormatted", new java.util.Date(task.getNextRunAt()).toString());

            if (task.getLastRunAt() != null) {
                taskObj.addProperty("lastRunAt", task.getLastRunAt());
                taskObj.addProperty("lastRunAtFormatted", new java.util.Date(task.getLastRunAt()).toString());
            } else {
                taskObj.addProperty("lastRunAt", "Never");
                taskObj.addProperty("lastRunAtFormatted", "Never");
            }

            taskObj.addProperty("status", task.getStatus());
            taskObj.addProperty("enabled", task.isEnabled());
            taskObj.addProperty("resultStorage", task.getResultStorage());
            taskObj.addProperty("conversationId", task.getConversationId());
            taskObj.addProperty("userName", task.getUserName());
            taskObj.addProperty("projectName", task.getProjectName());
            taskObj.addProperty("createdAt", task.getCreatedAt());
            taskObj.addProperty("createdAtFormatted", new java.util.Date(task.getCreatedAt()).toString());

            tasksArray.add(taskObj);

            if (task.isEnabled()) {
                activeCount++;
            } else {
                pausedCount++;
            }
        }

        JsonObject result = new JsonObject();
        result.add("tasks", tasksArray);
        result.addProperty("totalCount", tasks.size());
        result.addProperty("activeCount", activeCount);
        result.addProperty("pausedCount", pausedCount);
        result.addProperty("projectName", projectName);
        result.addProperty("userName", userName != null ? userName : "all");

        if (tasks.isEmpty()) {
            result.addProperty("message", "No scheduled tasks found for this project" + (userName != null ? " and user" : ""));
        }

        return result;
    }
}
