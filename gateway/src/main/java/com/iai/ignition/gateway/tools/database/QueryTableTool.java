package com.iai.ignition.gateway.tools.database;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.datasource.Datasource;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.sql.*;

/**
 * Tool for querying a database table with optional filters.
 */
public class QueryTableTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.database.QueryTableTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public QueryTableTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "query_table";
    }

    @Override
    public String getDescription() {
        return "Query a database table with optional WHERE clause filters. Returns rows with all columns. " +
                "Enforces queryTimeoutSeconds setting.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");

        JsonObject properties = new JsonObject();

        JsonObject database = new JsonObject();
        database.addProperty("type", "string");
        database.addProperty("description", "The database connection name");
        properties.add("database", database);

        JsonObject tableName = new JsonObject();
        tableName.addProperty("type", "string");
        tableName.addProperty("description", "The table name to query");
        properties.add("table_name", tableName);

        JsonObject filters = new JsonObject();
        filters.addProperty("type", "string");
        filters.addProperty("description", "Optional WHERE clause (without 'WHERE' keyword)");
        properties.add("filters", filters);

        JsonObject limit = new JsonObject();
        limit.addProperty("type", "integer");
        limit.addProperty("description", "Maximum number of rows to return (default: 100)");
        limit.addProperty("default", 100);
        properties.add("limit", limit);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("database");
        required.add("table_name");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String databaseName = params.get("database").getAsString();
        String tableName = params.get("table_name").getAsString();
        String filters = params.has("filters") ? params.get("filters").getAsString() : null;
        int limit = params.has("limit") ? params.get("limit").getAsInt() : 100;

        logger.debug("Querying table: " + databaseName + "." + tableName);

        Datasource datasource = gatewayContext.getDatasourceManager().getDatasource(databaseName);
        if (datasource == null) {
            throw new IllegalArgumentException("Database not found: " + databaseName);
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName);
        if (filters != null && !filters.isEmpty()) {
            sql.append(" WHERE ").append(filters);
        }
        sql.append(" LIMIT ").append(limit);

        JsonArray rows = new JsonArray();

        try (Connection conn = datasource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.setQueryTimeout(settings.getQueryTimeoutSeconds());

            try (ResultSet rs = stmt.executeQuery(sql.toString())) {
                ResultSetMetaData rsmd = rs.getMetaData();
                int columnCount = rsmd.getColumnCount();

                while (rs.next()) {
                    JsonObject row = new JsonObject();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = rsmd.getColumnName(i);
                        Object value = rs.getObject(i);
                        if (value != null) {
                            row.addProperty(columnName, value.toString());
                        }
                    }
                    rows.add(row);
                }
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("database", databaseName);
        result.addProperty("table_name", tableName);
        result.add("rows", rows);
        result.addProperty("count", rows.size());

        return result;
    }
}
