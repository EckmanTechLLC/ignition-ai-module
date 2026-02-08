package com.iai.ignition.gateway.tools.tasks;

import com.iai.ignition.common.model.ScheduledTask;
import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.database.TaskDAO;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.text.ParseException;
import java.util.Calendar;
import java.util.UUID;

/**
 * Tool for creating scheduled tasks via natural language.
 * Allows the AI to create recurring tasks that execute prompts on a schedule.
 */
public class CreateScheduledTaskTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.tasks.CreateScheduledTaskTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;
    private String currentUserName;
    private String currentProjectName;

    public CreateScheduledTaskTool(GatewayContext gatewayContext, IAISettings settings) {
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
        return "create_scheduled_task";
    }

    @Override
    public String getDescription() {
        return "Create a scheduled task that executes an AI prompt on a recurring schedule. " +
               "Use this when the user asks to automate queries or create recurring tasks. " +
               "Supports cron expressions for flexible scheduling (e.g., '*/5 * * * *' for every 5 minutes, '0 6 * * *' for daily at 6 AM). " +
               "Each task execution creates a new isolated conversation to prevent token limit issues.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        // userName and projectName are now provided by context (not parameters)
        // Keeping them in schema but marking as optional

        // taskDescription
        JsonObject taskDescription = new JsonObject();
        taskDescription.addProperty("type", "string");
        taskDescription.addProperty("description", "Human-readable description of what the task does (e.g., 'Read ToggleMe tag every 5 minutes')");
        properties.add("taskDescription", taskDescription);

        // prompt
        JsonObject prompt = new JsonObject();
        prompt.addProperty("type", "string");
        prompt.addProperty("description", "The AI prompt to execute on each run (e.g., 'Read the value of tag [default]ToggleMe')");
        properties.add("prompt", prompt);

        // cronExpression
        JsonObject cronExpression = new JsonObject();
        cronExpression.addProperty("type", "string");
        cronExpression.addProperty("description", "Cron expression (5 fields: minute hour day month weekday). Examples: '*/5 * * * *' (every 5 min), '0 6 * * *' (daily 6 AM), '0 9 * * 1-5' (weekdays 9 AM)");
        properties.add("cronExpression", cronExpression);

        // Note: resultStorage removed - tasks always create new conversations (prevents token limit issues)

        schema.add("properties", properties);

        // Required fields (userName and projectName come from context, not parameters)
        JsonArray required = new JsonArray();
        required.add("taskDescription");
        required.add("prompt");
        required.add("cronExpression");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        // Use context values (set by ToolRegistry) instead of parameters
        String userName = currentUserName;
        String projectName = currentProjectName;

        String taskDescription = params.get("taskDescription").getAsString();
        String prompt = params.get("prompt").getAsString();
        String cronExpression = params.get("cronExpression").getAsString();

        // Validate context
        if (projectName == null || projectName.isEmpty()) {
            throw new IllegalStateException("Project name not available from conversation context");
        }

        logger.info("Creating scheduled task: " + taskDescription);

        String dbConnection = settings.getDatabaseConnection();
        if (dbConnection == null || dbConnection.isEmpty()) {
            throw new IllegalStateException("Database connection not configured in module settings");
        }

        // Calculate next run time
        long nextRunAt;
        try {
            nextRunAt = calculateNextRunTime(cronExpression);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid cron expression: " + e.getMessage());
        }

        // Create task object
        ScheduledTask task = new ScheduledTask();
        task.setId(UUID.randomUUID().toString());
        task.setUserName(userName);
        task.setProjectName(projectName);
        task.setTaskDescription(taskDescription);
        task.setConversationId(null); // Not used - tasks always create new conversations
        task.setPrompt(prompt);
        task.setCronExpression(cronExpression);
        task.setLastRunAt(null);
        task.setNextRunAt(nextRunAt);
        task.setStatus("PENDING");
        task.setResultStorage("NEW_CONVERSATION"); // Always create new conversation per execution
        task.setCreatedAt(System.currentTimeMillis());
        task.setEnabled(true);

        // Save to database
        boolean created = TaskDAO.createTask(
            gatewayContext.getDatasourceManager(),
            dbConnection,
            task
        );

        if (!created) {
            throw new Exception("Failed to create scheduled task in database");
        }

        logger.info("Scheduled task created successfully: " + task.getId());

        // Schedule task immediately (dynamic scheduling)
        com.iai.ignition.gateway.tasks.TaskSchedulerService scheduler =
            com.iai.ignition.gateway.GatewayHook.getTaskScheduler();
        if (scheduler != null) {
            scheduler.scheduleTask(task);
            logger.info("Task scheduled dynamically: " + task.getId());
        } else {
            logger.warn("TaskSchedulerService not available, task will be scheduled on next Gateway restart");
        }

        // Build response
        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("taskId", task.getId());
        result.addProperty("nextRunAt", nextRunAt);
        result.addProperty("nextRunAtFormatted", new java.util.Date(nextRunAt).toString());
        result.addProperty("cronExpression", cronExpression);
        result.addProperty("message", "Scheduled task created successfully. Task will execute: " + new java.util.Date(nextRunAt));

        return result;
    }

    /**
     * Calculate next run time based on cron expression.
     * Supports 5-field cron format: minute hour day month weekday
     * Simplified implementation - for full cron support, consider using Quartz CronExpression.
     */
    private long calculateNextRunTime(String cronExpressionStr) throws ParseException {
        String[] parts = cronExpressionStr.trim().split("\\s+");
        if (parts.length != 5) {
            throw new ParseException("Invalid cron format, expected 5 fields: minute hour day month weekday", 0);
        }

        Calendar now = Calendar.getInstance();
        Calendar next = (Calendar) now.clone();
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        next.add(Calendar.MINUTE, 1); // Start from next minute

        // Try up to 366 days (1 year) to find next valid time
        for (int attempts = 0; attempts < 366 * 24 * 60; attempts++) {
            if (matchesCron(next, parts)) {
                return next.getTimeInMillis();
            }
            next.add(Calendar.MINUTE, 1);
        }

        throw new ParseException("No valid execution time found in next year for: " + cronExpressionStr, 0);
    }

    /**
     * Check if a calendar time matches a cron expression.
     */
    private boolean matchesCron(Calendar cal, String[] cronParts) {
        int minute = cal.get(Calendar.MINUTE);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
        int month = cal.get(Calendar.MONTH) + 1; // Calendar.MONTH is 0-based
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1; // Convert to 0=Sunday

        return matchesField(minute, cronParts[0], 0, 59) &&
               matchesField(hour, cronParts[1], 0, 23) &&
               matchesField(dayOfMonth, cronParts[2], 1, 31) &&
               matchesField(month, cronParts[3], 1, 12) &&
               matchesField(dayOfWeek, cronParts[4], 0, 6);
    }

    /**
     * Check if a value matches a cron field.
     * Supports: asterisk (wildcard), specific values (5), ranges (1-5), lists (1,3,5), step values (star/5)
     */
    private boolean matchesField(int value, String field, int min, int max) {
        // Wildcard matches everything
        if ("*".equals(field)) {
            return true;
        }

        // Handle step values (e.g., "*/5")
        if (field.contains("/")) {
            String[] stepParts = field.split("/");
            if (stepParts.length == 2) {
                int step = Integer.parseInt(stepParts[1].trim());
                if ("*".equals(stepParts[0])) {
                    return value % step == 0;
                }
            }
        }

        // Handle lists (e.g., "1,3,5")
        if (field.contains(",")) {
            for (String part : field.split(",")) {
                if (matchesField(value, part.trim(), min, max)) {
                    return true;
                }
            }
            return false;
        }

        // Handle ranges (e.g., "1-5")
        if (field.contains("-")) {
            String[] range = field.split("-");
            if (range.length == 2) {
                try {
                    int rangeStart = Integer.parseInt(range[0].trim());
                    int rangeEnd = Integer.parseInt(range[1].trim());
                    return value >= rangeStart && value <= rangeEnd;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return false;
        }

        // Handle specific value
        try {
            return value == Integer.parseInt(field.trim());
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
