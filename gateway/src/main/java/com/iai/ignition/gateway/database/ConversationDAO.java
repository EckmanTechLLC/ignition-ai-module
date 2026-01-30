package com.iai.ignition.gateway.database;

import com.iai.ignition.common.model.Conversation;
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
 * Data access object for conversation operations.
 * Follows ConversationSchemaManager pattern for database access.
 */
public class ConversationDAO {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.database.ConversationDAO");

    /**
     * Create a new conversation in the database.
     *
     * @param datasourceManager The datasource manager
     * @param databaseConnectionName Name of the database connection
     * @param conversation The conversation to create
     * @return true if created successfully
     */
    public static boolean create(DatasourceManager datasourceManager, String databaseConnectionName, Conversation conversation) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return false;
        }

        String sql = "INSERT INTO iai_conversations (id, user_name, project_name, title, created_at, last_updated_at) VALUES (?, ?, ?, ?, ?, ?)";

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return false;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, conversation.getId());
                stmt.setString(2, conversation.getUserName());
                stmt.setString(3, conversation.getProjectName());
                stmt.setString(4, conversation.getTitle());
                stmt.setLong(5, conversation.getCreatedAt());
                stmt.setLong(6, conversation.getLastUpdatedAt());

                int rows = stmt.executeUpdate();
                return rows > 0;
            }
        } catch (SQLException e) {
            logger.error("Error creating conversation", e);
            return false;
        }
    }

    /**
     * Find a conversation by ID.
     *
     * @param datasourceManager The datasource manager
     * @param databaseConnectionName Name of the database connection
     * @param id The conversation ID
     * @return The conversation, or null if not found
     */
    public static Conversation findById(DatasourceManager datasourceManager, String databaseConnectionName, String id) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return null;
        }

        String sql = "SELECT id, user_name, project_name, title, created_at, last_updated_at FROM iai_conversations WHERE id = ?";

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
                        return mapResultSetToConversation(rs);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding conversation by ID", e);
        }

        return null;
    }

    /**
     * List conversations for a user.
     *
     * @param datasourceManager The datasource manager
     * @param databaseConnectionName Name of the database connection
     * @param userName The user name
     * @param limit Maximum number of conversations to return
     * @return List of conversations
     */
    public static List<Conversation> listByUser(DatasourceManager datasourceManager, String databaseConnectionName, String userName, int limit) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return new ArrayList<>();
        }

        String sql = "SELECT id, user_name, project_name, title, created_at, last_updated_at FROM iai_conversations WHERE user_name = ? ORDER BY last_updated_at DESC LIMIT ?";

        List<Conversation> conversations = new ArrayList<>();

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return conversations;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, userName);
                stmt.setInt(2, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        conversations.add(mapResultSetToConversation(rs));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error listing conversations by user", e);
        }

        return conversations;
    }

    /**
     * List conversations for a project.
     *
     * @param datasourceManager The datasource manager
     * @param databaseConnectionName Name of the database connection
     * @param projectName The project name
     * @param limit Maximum number of conversations to return
     * @return List of conversations
     */
    public static List<Conversation> listByProject(DatasourceManager datasourceManager, String databaseConnectionName, String projectName, int limit) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return new ArrayList<>();
        }

        String sql = "SELECT id, user_name, project_name, title, created_at, last_updated_at FROM iai_conversations WHERE project_name = ? ORDER BY last_updated_at DESC LIMIT ?";

        List<Conversation> conversations = new ArrayList<>();

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return conversations;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, projectName);
                stmt.setInt(2, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        conversations.add(mapResultSetToConversation(rs));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error listing conversations by project", e);
        }

        return conversations;
    }

    /**
     * Update a conversation.
     *
     * @param datasourceManager The datasource manager
     * @param databaseConnectionName Name of the database connection
     * @param conversation The conversation to update
     * @return true if updated successfully
     */
    public static boolean update(DatasourceManager datasourceManager, String databaseConnectionName, Conversation conversation) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return false;
        }

        String sql = "UPDATE iai_conversations SET title = ?, last_updated_at = ? WHERE id = ?";

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return false;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, conversation.getTitle());
                stmt.setLong(2, conversation.getLastUpdatedAt());
                stmt.setString(3, conversation.getId());

                int rows = stmt.executeUpdate();
                return rows > 0;
            }
        } catch (SQLException e) {
            logger.error("Error updating conversation", e);
            return false;
        }
    }

    /**
     * Delete a conversation by ID.
     * Due to ON DELETE CASCADE, this will also delete all associated messages.
     *
     * @param datasourceManager The datasource manager
     * @param databaseConnectionName Name of the database connection
     * @param id The conversation ID
     * @return true if deleted successfully
     */
    public static boolean delete(DatasourceManager datasourceManager, String databaseConnectionName, String id) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured.");
            return false;
        }

        String sql = "DELETE FROM iai_conversations WHERE id = ?";

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return false;
            }

            try (Connection conn = datasource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, id);

                int rows = stmt.executeUpdate();
                return rows > 0;
            }
        } catch (SQLException e) {
            logger.error("Error deleting conversation", e);
            return false;
        }
    }

    /**
     * Map a ResultSet row to a Conversation object.
     */
    private static Conversation mapResultSetToConversation(ResultSet rs) throws SQLException {
        Conversation conversation = new Conversation();
        conversation.setId(rs.getString("id"));
        conversation.setUserName(rs.getString("user_name"));
        conversation.setProjectName(rs.getString("project_name"));
        conversation.setTitle(rs.getString("title"));
        conversation.setCreatedAt(rs.getLong("created_at"));
        conversation.setLastUpdatedAt(rs.getLong("last_updated_at"));
        return conversation;
    }
}
