package com.iai.ignition.gateway.database;

import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.datasource.Datasource;
import com.inductiveautomation.ignition.gateway.datasource.DatasourceManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages database schema for IAI conversations and messages.
 * Creates tables in the configured database connection.
 */
public class ConversationSchemaManager {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.database.ConversationSchemaManager");

    /**
     * SQL to create conversations table.
     */
    private static final String CREATE_CONVERSATIONS_TABLE =
        "CREATE TABLE IF NOT EXISTS iai_conversations (" +
        "    id VARCHAR(36) PRIMARY KEY," +
        "    user_name VARCHAR(255)," +
        "    project_name VARCHAR(255) NOT NULL," +
        "    title VARCHAR(500)," +
        "    created_at BIGINT NOT NULL," +
        "    last_updated_at BIGINT NOT NULL" +
        ")";

    /**
     * SQL to create conversations indexes.
     */
    private static final String[] CONVERSATIONS_INDEXES = {
        "CREATE INDEX IF NOT EXISTS idx_conv_user ON iai_conversations(user_name)",
        "CREATE INDEX IF NOT EXISTS idx_conv_project ON iai_conversations(project_name)",
        "CREATE INDEX IF NOT EXISTS idx_conv_updated ON iai_conversations(last_updated_at)"
    };

    /**
     * SQL to create messages table.
     */
    private static final String CREATE_MESSAGES_TABLE =
        "CREATE TABLE IF NOT EXISTS iai_messages (" +
        "    id VARCHAR(36) PRIMARY KEY," +
        "    conversation_id VARCHAR(36) NOT NULL," +
        "    role VARCHAR(20) NOT NULL," +
        "    content TEXT," +
        "    tool_calls TEXT," +
        "    tool_results TEXT," +
        "    input_tokens INTEGER," +
        "    output_tokens INTEGER," +
        "    timestamp BIGINT NOT NULL," +
        "    FOREIGN KEY (conversation_id) REFERENCES iai_conversations(id) ON DELETE CASCADE" +
        ")";

    /**
     * SQL to create messages indexes.
     */
    private static final String[] MESSAGES_INDEXES = {
        "CREATE INDEX IF NOT EXISTS idx_msg_conv ON iai_messages(conversation_id)",
        "CREATE INDEX IF NOT EXISTS idx_msg_timestamp ON iai_messages(timestamp)"
    };

    /**
     * Create conversation and message tables in the specified database.
     *
     * @param datasourceManager The datasource manager
     * @param databaseConnectionName Name of the database connection
     * @return true if tables were created successfully
     */
    public static boolean createTables(DatasourceManager datasourceManager, String databaseConnectionName) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            logger.error("Database connection name is not configured. Cannot create tables.");
            return false;
        }

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                logger.error("Database connection not found: " + databaseConnectionName);
                return false;
            }

            try (Connection conn = datasource.getConnection()) {
                // Create conversations table
                executeUpdate(conn, CREATE_CONVERSATIONS_TABLE);
                logger.info("Conversations table created or verified.");

                // Create conversations indexes
                for (String indexSql : CONVERSATIONS_INDEXES) {
                    executeUpdate(conn, indexSql);
                }
                logger.info("Conversations indexes created or verified.");

                // Create messages table
                executeUpdate(conn, CREATE_MESSAGES_TABLE);
                logger.info("Messages table created or verified.");

                // Create messages indexes
                for (String indexSql : MESSAGES_INDEXES) {
                    executeUpdate(conn, indexSql);
                }
                logger.info("Messages indexes created or verified.");

                logger.info("IAI database schema setup completed successfully.");
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error creating IAI database tables", e);
            return false;
        }
    }

    /**
     * Execute a SQL update statement.
     */
    private static void executeUpdate(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    /**
     * Verify that tables exist in the database.
     *
     * @param datasourceManager The datasource manager
     * @param databaseConnectionName Name of the database connection
     * @return true if tables exist
     */
    public static boolean verifyTables(DatasourceManager datasourceManager, String databaseConnectionName) {
        if (databaseConnectionName == null || databaseConnectionName.isEmpty()) {
            return false;
        }

        try {
            Datasource datasource = datasourceManager.getDatasource(databaseConnectionName);
            if (datasource == null) {
                return false;
            }

            try (Connection conn = datasource.getConnection()) {
                // Try to query the tables
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeQuery("SELECT 1 FROM iai_conversations LIMIT 1");
                    stmt.executeQuery("SELECT 1 FROM iai_messages LIMIT 1");
                }
                return true;
            }
        } catch (SQLException e) {
            logger.debug("Tables do not exist or cannot be accessed: " + e.getMessage());
            return false;
        }
    }
}
