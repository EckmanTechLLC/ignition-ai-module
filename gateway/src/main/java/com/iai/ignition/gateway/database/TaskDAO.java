package com.iai.ignition.gateway.database;

import com.iai.ignition.common.model.ScheduledTask;
import com.iai.ignition.common.model.TaskExecution;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.datasource.Datasource;
import com.inductiveautomation.ignition.gateway.datasource.DatasourceManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for scheduled task operations.
 * Manages iai_scheduled_tasks and iai_task_executions tables.
 */
public class TaskDAO {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.database.TaskDAO");

    /**
     * Create a new scheduled task in the database.
     */
    public static boolean createTask(DatasourceManager datasourceManager, String databaseConnectionName, ScheduledTask task) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return false;
        }

        String sql = "INSERT INTO iai_scheduled_tasks (id, user_name, project_name, task_description, conversation_id, prompt, cron_expression, last_run_at, next_run_at, status, result_storage, created_at, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return false;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, task.getId());
                stmt.setString(2, task.getUserName());
                stmt.setString(3, task.getProjectName());
                stmt.setString(4, task.getTaskDescription());
                stmt.setString(5, task.getConversationId());
                stmt.setString(6, task.getPrompt());
                stmt.setString(7, task.getCronExpression());
                stmt.setObject(8, task.getLastRunAt());
                stmt.setLong(9, task.getNextRunAt());
                stmt.setString(10, task.getStatus());
                stmt.setString(11, task.getResultStorage());
                stmt.setLong(12, task.getCreatedAt());
                stmt.setBoolean(13, task.isEnabled());

                int rows = stmt.executeUpdate();

                // Explicitly commit to ensure task is persisted before scheduling
                // Prevents race condition where task executes before INSERT commits
                if (!conn.getAutoCommit()) {
                    conn.commit();
                }

                return rows > 0;
            }
        } catch (SQLException e) {
            logger.error("Error creating scheduled task", e);
            return false;
        }
    }

    /**
     * Get a scheduled task by ID.
     */
    public static ScheduledTask getTask(DatasourceManager datasourceManager, String databaseConnectionName, String taskId) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return null;
        }

        String sql = "SELECT id, user_name, project_name, task_description, conversation_id, prompt, cron_expression, last_run_at, next_run_at, status, result_storage, created_at, enabled FROM iai_scheduled_tasks WHERE id = ?";

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return null;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, taskId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSetToTask(rs);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting scheduled task", e);
        }

        return null;
    }

    /**
     * Get tasks for a specific user and project.
     */
    public static List<ScheduledTask> getTasksByUser(DatasourceManager datasourceManager, String databaseConnectionName, String userName, String projectName) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return new ArrayList<>();
        }

        String sql = "SELECT id, user_name, project_name, task_description, conversation_id, prompt, cron_expression, last_run_at, next_run_at, status, result_storage, created_at, enabled FROM iai_scheduled_tasks WHERE user_name = ? AND project_name = ? ORDER BY created_at DESC";

        List<ScheduledTask> tasks = new ArrayList<>();

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return tasks;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, userName);
                stmt.setString(2, projectName);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        tasks.add(mapResultSetToTask(rs));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting tasks by user", e);
        }

        return tasks;
    }

    /**
     * Get all active (enabled) tasks for scheduling.
     */
    public static List<ScheduledTask> getActiveTasks(DatasourceManager datasourceManager, String databaseConnectionName) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return new ArrayList<>();
        }

        String sql = "SELECT id, user_name, project_name, task_description, conversation_id, prompt, cron_expression, last_run_at, next_run_at, status, result_storage, created_at, enabled FROM iai_scheduled_tasks WHERE enabled = TRUE ORDER BY next_run_at ASC";

        List<ScheduledTask> tasks = new ArrayList<>();

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return tasks;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        tasks.add(mapResultSetToTask(rs));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting active tasks", e);
        }

        return tasks;
    }

    /**
     * Update task status.
     */
    public static boolean updateTaskStatus(DatasourceManager datasourceManager, String databaseConnectionName, String taskId, String status) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return false;
        }

        String sql = "UPDATE iai_scheduled_tasks SET status = ? WHERE id = ?";

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return false;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, status);
                stmt.setString(2, taskId);

                int rows = stmt.executeUpdate();
                return rows > 0;
            }
        } catch (SQLException e) {
            logger.error("Error updating task status", e);
            return false;
        }
    }

    /**
     * Update task next run time and last run time.
     */
    public static boolean updateNextRunTime(DatasourceManager datasourceManager, String databaseConnectionName, String taskId, long nextRunAt, long lastRunAt) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return false;
        }

        String sql = "UPDATE iai_scheduled_tasks SET next_run_at = ?, last_run_at = ? WHERE id = ?";

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return false;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setLong(1, nextRunAt);
                stmt.setLong(2, lastRunAt);
                stmt.setString(3, taskId);

                int rows = stmt.executeUpdate();
                return rows > 0;
            }
        } catch (SQLException e) {
            logger.error("Error updating task next run time", e);
            return false;
        }
    }

    /**
     * Update task enabled status.
     */
    public static boolean updateTaskEnabled(DatasourceManager datasourceManager, String databaseConnectionName, String taskId, boolean enabled) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return false;
        }

        String sql = "UPDATE iai_scheduled_tasks SET enabled = ? WHERE id = ?";

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return false;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setBoolean(1, enabled);
                stmt.setString(2, taskId);

                int rows = stmt.executeUpdate();
                return rows > 0;
            }
        } catch (SQLException e) {
            logger.error("Error updating task enabled status", e);
            return false;
        }
    }

    /**
     * Delete a scheduled task.
     */
    public static boolean deleteTask(DatasourceManager datasourceManager, String databaseConnectionName, String taskId) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return false;
        }

        String sql = "DELETE FROM iai_scheduled_tasks WHERE id = ?";

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return false;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, taskId);

                int rows = stmt.executeUpdate();
                return rows > 0;
            }
        } catch (SQLException e) {
            logger.error("Error deleting scheduled task", e);
            return false;
        }
    }

    /**
     * Record a task execution in history.
     */
    public static boolean recordExecution(DatasourceManager datasourceManager, String databaseConnectionName, TaskExecution execution) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return false;
        }

        String sql = "INSERT INTO iai_task_executions (id, task_id, executed_at, conversation_id, status, error_message, execution_time_ms) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return false;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, execution.getId());
                stmt.setString(2, execution.getTaskId());
                stmt.setLong(3, execution.getExecutedAt());
                stmt.setString(4, execution.getConversationId());
                stmt.setString(5, execution.getStatus());
                stmt.setString(6, execution.getErrorMessage());
                stmt.setObject(7, execution.getExecutionTimeMs());

                int rows = stmt.executeUpdate();
                return rows > 0;
            }
        } catch (SQLException e) {
            logger.error("Error recording task execution", e);
            return false;
        }
    }

    /**
     * Get execution history for a task.
     */
    public static List<TaskExecution> getTaskExecutions(DatasourceManager datasourceManager, String databaseConnectionName, String taskId, int limit) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return new ArrayList<>();
        }

        String sql;
        if (limit > 0) {
            sql = "SELECT id, task_id, executed_at, conversation_id, status, error_message, execution_time_ms FROM iai_task_executions WHERE task_id = ? ORDER BY executed_at DESC LIMIT ?";
        } else {
            sql = "SELECT id, task_id, executed_at, conversation_id, status, error_message, execution_time_ms FROM iai_task_executions WHERE task_id = ? ORDER BY executed_at DESC";
        }

        List<TaskExecution> executions = new ArrayList<>();

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return executions;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, taskId);
                if (limit > 0) {
                    stmt.setInt(2, limit);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        executions.add(mapResultSetToExecution(rs));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting task executions", e);
        }

        return executions;
    }

    /**
     * Map ResultSet to ScheduledTask.
     */
    private static ScheduledTask mapResultSetToTask(ResultSet rs) throws SQLException {
        ScheduledTask task = new ScheduledTask();
        task.setId(rs.getString("id"));
        task.setUserName(rs.getString("user_name"));
        task.setProjectName(rs.getString("project_name"));
        task.setTaskDescription(rs.getString("task_description"));
        task.setConversationId(rs.getString("conversation_id"));
        task.setPrompt(rs.getString("prompt"));
        task.setCronExpression(rs.getString("cron_expression"));
        task.setLastRunAt((Long) rs.getObject("last_run_at"));
        task.setNextRunAt(rs.getLong("next_run_at"));
        task.setStatus(rs.getString("status"));
        task.setResultStorage(rs.getString("result_storage"));
        task.setCreatedAt(rs.getLong("created_at"));
        task.setEnabled(rs.getBoolean("enabled"));
        return task;
    }

    /**
     * Map ResultSet to TaskExecution.
     */
    private static TaskExecution mapResultSetToExecution(ResultSet rs) throws SQLException {
        TaskExecution execution = new TaskExecution();
        execution.setId(rs.getString("id"));
        execution.setTaskId(rs.getString("task_id"));
        execution.setExecutedAt(rs.getLong("executed_at"));
        execution.setConversationId(rs.getString("conversation_id"));
        execution.setStatus(rs.getString("status"));
        execution.setErrorMessage(rs.getString("error_message"));
        execution.setExecutionTimeMs((Integer) rs.getObject("execution_time_ms"));
        return execution;
    }
}
