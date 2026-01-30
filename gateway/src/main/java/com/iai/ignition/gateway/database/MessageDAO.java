package com.iai.ignition.gateway.database;

import com.inductiveautomation.ignition.common.gson.Gson;
import com.inductiveautomation.ignition.common.gson.reflect.TypeToken;
import com.iai.ignition.common.model.Message;
import com.iai.ignition.common.model.ToolCall;
import com.iai.ignition.common.model.ToolResult;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.datasource.Datasource;
import com.inductiveautomation.ignition.gateway.datasource.DatasourceManager;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for message operations.
 * Follows ConversationSchemaManager pattern for database access.
 */
public class MessageDAO {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.database.MessageDAO");
    private static final Gson gson = new Gson();
    private static final Type TOOL_CALL_LIST_TYPE = new TypeToken<List<ToolCall>>(){}.getType();
    private static final Type TOOL_RESULT_LIST_TYPE = new TypeToken<List<ToolResult>>(){}.getType();

    /**
     * Create a new message in the database.
     *
     * @param datasourceManager The datasource manager
     * @param databaseConnectionName Name of the database connection
     * @param message The message to create
     * @return true if created successfully
     */
    public static boolean create(DatasourceManager datasourceManager, String databaseConnectionName, Message message) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return false;
        }

        String sql = "INSERT INTO iai_messages (id, conversation_id, role, content, tool_calls, tool_results, input_tokens, output_tokens, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return false;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, message.getId());
                stmt.setString(2, message.getConversationId());
                stmt.setString(3, message.getRole());
                stmt.setString(4, message.getContent());

                // Serialize tool_calls to JSON
                String toolCallsJson = message.getToolCalls() != null ? gson.toJson(message.getToolCalls()) : null;
                stmt.setString(5, toolCallsJson);

                // Serialize tool_results to JSON
                String toolResultsJson = message.getToolResults() != null ? gson.toJson(message.getToolResults()) : null;
                stmt.setString(6, toolResultsJson);

                stmt.setObject(7, message.getInputTokens());
                stmt.setObject(8, message.getOutputTokens());
                stmt.setLong(9, message.getTimestamp());

                int rows = stmt.executeUpdate();
                return rows > 0;
            }
        } catch (SQLException e) {
            logger.error("Error creating message", e);
            return false;
        }
    }

    /**
     * Find a message by ID.
     *
     * @param datasourceManager The datasource manager
     * @param databaseConnectionName Name of the database connection
     * @param id The message ID
     * @return The message, or null if not found
     */
    public static Message findById(DatasourceManager datasourceManager, String databaseConnectionName, String id) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return null;
        }

        String sql = "SELECT id, conversation_id, role, content, tool_calls, tool_results, input_tokens, output_tokens, timestamp FROM iai_messages WHERE id = ?";

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return null;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, id);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSetToMessage(rs);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding message by ID", e);
        }

        return null;
    }

    /**
     * List messages for a conversation.
     *
     * @param datasourceManager The datasource manager
     * @param databaseConnectionName Name of the database connection
     * @param conversationId The conversation ID
     * @param limit Maximum number of messages to return (0 for all)
     * @return List of messages ordered by timestamp
     */
    public static List<Message> listByConversation(DatasourceManager datasourceManager, String databaseConnectionName, String conversationId, int limit) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return new ArrayList<>();
        }

        String sql;
        if (limit > 0) {
            sql = "SELECT id, conversation_id, role, content, tool_calls, tool_results, input_tokens, output_tokens, timestamp FROM iai_messages WHERE conversation_id = ? ORDER BY timestamp ASC LIMIT ?";
        } else {
            sql = "SELECT id, conversation_id, role, content, tool_calls, tool_results, input_tokens, output_tokens, timestamp FROM iai_messages WHERE conversation_id = ? ORDER BY timestamp ASC";
        }

        List<Message> messages = new ArrayList<>();

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return messages;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, conversationId);
                if (limit > 0) {
                    stmt.setInt(2, limit);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        messages.add(mapResultSetToMessage(rs));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error listing messages by conversation", e);
        }

        return messages;
    }

    /**
     * Count messages in a conversation.
     *
     * @param datasourceManager The datasource manager
     * @param databaseConnectionName Name of the database connection
     * @param conversationId The conversation ID
     * @return Number of messages in the conversation
     */
    public static int countByConversation(DatasourceManager datasourceManager, String databaseConnectionName, String conversationId) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return 0;
        }

        String sql = "SELECT COUNT(*) FROM iai_messages WHERE conversation_id = ?";

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return 0;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, conversationId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error counting messages by conversation", e);
        }

        return 0;
    }

    /**
     * Map a ResultSet row to a Message object.
     */
    private static Message mapResultSetToMessage(ResultSet rs) throws SQLException {
        Message message = new Message();
        message.setId(rs.getString("id"));
        message.setConversationId(rs.getString("conversation_id"));
        message.setRole(rs.getString("role"));
        message.setContent(rs.getString("content"));

        // Deserialize tool_calls from JSON
        String toolCallsJson = rs.getString("tool_calls");
        if (toolCallsJson != null && !toolCallsJson.isEmpty()) {
            List<ToolCall> toolCalls = gson.fromJson(toolCallsJson, TOOL_CALL_LIST_TYPE);
            message.setToolCalls(toolCalls);
        }

        // Deserialize tool_results from JSON
        String toolResultsJson = rs.getString("tool_results");
        if (toolResultsJson != null && !toolResultsJson.isEmpty()) {
            List<ToolResult> toolResults = gson.fromJson(toolResultsJson, TOOL_RESULT_LIST_TYPE);
            message.setToolResults(toolResults);
        }

        message.setInputTokens((Integer) rs.getObject("input_tokens"));
        message.setOutputTokens((Integer) rs.getObject("output_tokens"));
        message.setTimestamp(rs.getLong("timestamp"));

        return message;
    }
}
