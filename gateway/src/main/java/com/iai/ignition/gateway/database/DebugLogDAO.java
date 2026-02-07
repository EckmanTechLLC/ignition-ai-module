package com.iai.ignition.gateway.database;

import com.iai.ignition.common.model.DebugLog;
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
 * Data access object for debug log operations.
 * Stores full request/response JSON for LLM API calls.
 */
public class DebugLogDAO {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.database.DebugLogDAO");

    /**
     * Create a new debug log entry in the database.
     *
     * @param datasourceManager The datasource manager
     * @param databaseConnectionName Name of the database connection
     * @param debugLog The debug log to create
     * @return true if created successfully
     */
    public static boolean create(DatasourceManager datasourceManager, String databaseConnectionName, DebugLog debugLog) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return false;
        }

        String sql = "INSERT INTO iai_debug_log (id, message_id, request_json, response_json, timestamp) VALUES (?, ?, ?, ?, ?)";

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return false;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, debugLog.getId());
                stmt.setString(2, debugLog.getMessageId());
                stmt.setString(3, debugLog.getRequestJson());
                stmt.setString(4, debugLog.getResponseJson());
                stmt.setLong(5, debugLog.getTimestamp());

                int rows = stmt.executeUpdate();
                return rows > 0;
            }
        } catch (SQLException e) {
            logger.error("Error creating debug log", e);
            return false;
        }
    }

    /**
     * Find debug logs by message ID.
     *
     * @param datasourceManager The datasource manager
     * @param databaseConnectionName Name of the database connection
     * @param messageId The message ID
     * @return List of debug logs for this message
     */
    public static List<DebugLog> findByMessageId(DatasourceManager datasourceManager, String databaseConnectionName, String messageId) {
        List<DebugLog> logs = new ArrayList<>();

        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return logs;
        }

        String sql = "SELECT id, message_id, request_json, response_json, timestamp FROM iai_debug_log WHERE message_id = ? ORDER BY timestamp ASC";

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return logs;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, messageId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        DebugLog log = new DebugLog();
                        log.setId(rs.getString("id"));
                        log.setMessageId(rs.getString("message_id"));
                        log.setRequestJson(rs.getString("request_json"));
                        log.setResponseJson(rs.getString("response_json"));
                        log.setTimestamp(rs.getLong("timestamp"));
                        logs.add(log);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error querying debug logs", e);
        }

        return logs;
    }

    /**
     * Find all debug logs for a conversation.
     *
     * @param datasourceManager The datasource manager
     * @param databaseConnectionName Name of the database connection
     * @param conversationId The conversation ID
     * @return List of debug logs for this conversation
     */
    public static List<DebugLog> findByConversationId(DatasourceManager datasourceManager, String databaseConnectionName, String conversationId) {
        List<DebugLog> logs = new ArrayList<>();

        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return logs;
        }

        String sql = "SELECT d.id, d.message_id, d.request_json, d.response_json, d.timestamp " +
                     "FROM iai_debug_log d " +
                     "JOIN iai_messages m ON d.message_id = m.id " +
                     "WHERE m.conversation_id = ? " +
                     "ORDER BY d.timestamp ASC";

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return logs;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, conversationId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        DebugLog log = new DebugLog();
                        log.setId(rs.getString("id"));
                        log.setMessageId(rs.getString("message_id"));
                        log.setRequestJson(rs.getString("request_json"));
                        log.setResponseJson(rs.getString("response_json"));
                        log.setTimestamp(rs.getLong("timestamp"));
                        logs.add(log);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error querying debug logs for conversation", e);
        }

        return logs;
    }
}
