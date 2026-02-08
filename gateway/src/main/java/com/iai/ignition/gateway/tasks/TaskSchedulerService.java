package com.iai.ignition.gateway.tasks;

import com.iai.ignition.common.model.*;
import com.iai.ignition.gateway.llm.ClaudeAPIClient;
import com.iai.ignition.gateway.database.ConversationDAO;
import com.iai.ignition.gateway.database.MessageDAO;
import com.iai.ignition.gateway.database.TaskDAO;
import com.iai.ignition.gateway.endpoints.ConversationEndpoints;
import com.iai.ignition.gateway.records.IAISettings;
import com.iai.ignition.gateway.tools.ToolRegistry;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import java.text.ParseException;
import java.util.*;
import java.util.Calendar;
import java.util.concurrent.*;

/**
 * Service for scheduling and executing recurring AI tasks.
 * Loads active tasks from database and schedules them using cron expressions.
 */
public class TaskSchedulerService {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tasks.TaskSchedulerService");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks;

    public TaskSchedulerService(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
        this.scheduler = Executors.newScheduledThreadPool(4); // Pool of 4 threads for task execution
        this.scheduledTasks = new ConcurrentHashMap<>();
    }

    /**
     * Start the scheduler and load active tasks from database.
     */
    public void start() {
        logger.info("Starting TaskSchedulerService");

        try {
            String dbConnection = settings.getDatabaseConnection();
            if (dbConnection == null || dbConnection.isEmpty()) {
                logger.warn("Database connection not configured, task scheduling disabled");
                return;
            }

            // Load active tasks from database
            List<ScheduledTask> activeTasks = TaskDAO.getActiveTasks(
                gatewayContext.getDatasourceManager(),
                dbConnection
            );

            logger.info("Loaded " + activeTasks.size() + " active tasks");

            // Schedule each task
            for (ScheduledTask task : activeTasks) {
                try {
                    scheduleTask(task);
                } catch (Exception e) {
                    logger.error("Error scheduling task " + task.getId(), e);
                }
            }

        } catch (Exception e) {
            logger.error("Error starting TaskSchedulerService", e);
        }
    }

    /**
     * Stop the scheduler and cancel all scheduled tasks.
     */
    public void stop() {
        logger.info("Stopping TaskSchedulerService");

        // Cancel all scheduled tasks
        for (Map.Entry<String, ScheduledFuture<?>> entry : scheduledTasks.entrySet()) {
            entry.getValue().cancel(false);
        }
        scheduledTasks.clear();

        // Shutdown scheduler gracefully
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("TaskSchedulerService stopped");
    }

    /**
     * Schedule a task for execution.
     */
    public void scheduleTask(ScheduledTask task) {
        String taskId = task.getId();

        // Unschedule if already scheduled
        if (scheduledTasks.containsKey(taskId)) {
            unscheduleTask(taskId);
        }

        try {
            // Calculate delay until next run
            long now = System.currentTimeMillis();
            long nextRun = task.getNextRunAt();
            long delay = nextRun - now;

            if (delay < 0) {
                // Next run is in the past, recalculate
                nextRun = calculateNextRunTime(task.getCronExpression());
                delay = nextRun - now;

                // Update next run time in database
                TaskDAO.updateNextRunTime(
                    gatewayContext.getDatasourceManager(),
                    settings.getDatabaseConnection(),
                    taskId,
                    nextRun,
                    task.getLastRunAt() != null ? task.getLastRunAt() : 0
                );
            }

            // Schedule task execution
            ScheduledFuture<?> future = scheduler.schedule(
                () -> executeTaskWrapper(task),
                delay,
                TimeUnit.MILLISECONDS
            );

            scheduledTasks.put(taskId, future);
            logger.debug("Scheduled task " + taskId + " to run in " + (delay / 1000) + " seconds");

        } catch (Exception e) {
            logger.error("Error scheduling task " + taskId, e);
        }
    }

    /**
     * Unschedule a task.
     */
    public void unscheduleTask(String taskId) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null) {
            future.cancel(false);
            logger.debug("Unscheduled task " + taskId);
        }
    }

    /**
     * Wrapper for task execution that handles errors and rescheduling.
     */
    private void executeTaskWrapper(ScheduledTask task) {
        String taskId = task.getId();

        try {
            // Execute the task
            executeTask(task);

            // Calculate next run time
            long nextRun = calculateNextRunTime(task.getCronExpression());
            long now = System.currentTimeMillis();

            // Update task in database
            TaskDAO.updateNextRunTime(
                gatewayContext.getDatasourceManager(),
                settings.getDatabaseConnection(),
                taskId,
                nextRun,
                now
            );

            // Reload task from database to get updated state
            ScheduledTask updatedTask = TaskDAO.getTask(
                gatewayContext.getDatasourceManager(),
                settings.getDatabaseConnection(),
                taskId
            );

            if (updatedTask != null && updatedTask.isEnabled()) {
                // Reschedule for next run
                scheduleTask(updatedTask);
            }

        } catch (Exception e) {
            logger.error("Error in task execution wrapper for " + taskId, e);

            // Try to reschedule anyway
            try {
                ScheduledTask updatedTask = TaskDAO.getTask(
                    gatewayContext.getDatasourceManager(),
                    settings.getDatabaseConnection(),
                    taskId
                );

                if (updatedTask != null && updatedTask.isEnabled()) {
                    scheduleTask(updatedTask);
                }
            } catch (Exception ex) {
                logger.error("Error rescheduling task after failure: " + taskId, ex);
            }
        } finally {
            // Remove from active map (will be re-added when rescheduled)
            scheduledTasks.remove(taskId);
        }
    }

    /**
     * Execute a scheduled task.
     * Creates a conversation, sends the prompt, and calls the AI.
     */
    private void executeTask(ScheduledTask task) {
        String taskId = task.getId();
        long startTime = System.currentTimeMillis();

        logger.info("Executing scheduled task: " + taskId);

        TaskExecution execution = new TaskExecution();
        execution.setId(UUID.randomUUID().toString());
        execution.setTaskId(taskId);
        execution.setExecutedAt(startTime);

        try {
            String dbConnection = settings.getDatabaseConnection();

            // Always create new conversation for each task execution (prevents token limit issues)
            Conversation conversation = new Conversation();
            conversation.setId(UUID.randomUUID().toString());
            conversation.setUserName(task.getUserName());
            conversation.setProjectName(task.getProjectName());
            conversation.setTitle("[Task] " + task.getTaskDescription());
            conversation.setCreatedAt(startTime);
            conversation.setLastUpdatedAt(startTime);

            ConversationDAO.create(gatewayContext.getDatasourceManager(), dbConnection, conversation);

            execution.setConversationId(conversation.getId());

            // Save user message (task prompt)
            Message userMessage = new Message();
            userMessage.setId(UUID.randomUUID().toString());
            userMessage.setConversationId(conversation.getId());
            userMessage.setRole("user");
            userMessage.setContent(task.getPrompt());
            userMessage.setTimestamp(startTime);

            MessageDAO.create(gatewayContext.getDatasourceManager(), dbConnection, userMessage);

            // Initialize Claude API client
            ClaudeAPIClient claudeClient = new ClaudeAPIClient(settings.getApiKey());

            // Initialize tool registry
            ToolRegistry toolRegistry = new ToolRegistry(gatewayContext, settings);

            // Process message with AI (using full ConversationEndpoints logic)
            // Note: processWithAI() already saves the assistant message to the database
            Message assistantMessage = ConversationEndpoints.processWithAI(
                gatewayContext,
                settings,
                claudeClient,
                toolRegistry,
                conversation,
                dbConnection,
                false, // Disable auto-compaction for tasks
                180000, // Not used when compaction disabled
                30 // Not used when compaction disabled
            );

            // Record successful execution (message already saved by processWithAI)
            execution.setStatus("SUCCESS");
            execution.setExecutionTimeMs((int) (System.currentTimeMillis() - startTime));

            logger.info("Task executed successfully: " + taskId);

        } catch (Exception e) {
            logger.error("Error executing task " + taskId, e);

            // Record failed execution
            execution.setStatus("FAILED");
            execution.setErrorMessage(e.getMessage());
            execution.setExecutionTimeMs((int) (System.currentTimeMillis() - startTime));
        }

        // Save execution record
        try {
            TaskDAO.recordExecution(
                gatewayContext.getDatasourceManager(),
                settings.getDatabaseConnection(),
                execution
            );
        } catch (Exception e) {
            logger.error("Error recording task execution", e);
        }
    }

    /**
     * Calculate next run time based on cron expression.
     * Supports standard 5-field cron format: minute hour dayOfMonth month dayOfWeek
     * Supports: specific values, wildcards (*), ranges (1-5), lists (1,3,5)
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
     * Supports: * (wildcard), specific values (5), ranges (1-5), lists (1,3,5)
     */
    private boolean matchesField(int value, String field, int min, int max) {
        // Wildcard matches everything
        if ("*".equals(field)) {
            return true;
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
