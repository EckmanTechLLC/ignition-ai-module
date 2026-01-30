package com.iai.ignition.gateway.tools.database;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.datasource.Datasource;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

/**
 * Tool for describing a database table's schema.
 */
public class DescribeTableTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.database.DescribeTableTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public DescribeTableTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "describe_table";
    }

    @Override
    public String getDescription() {
        return "Describe a database table's schema. Returns column names, data types, constraints, and keys.";
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
        tableName.addProperty("description", "The table name to describe");
        properties.add("table_name", tableName);

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

        logger.debug("Describing table: " + databaseName + "." + tableName);

        Datasource datasource = gatewayContext.getDatasourceManager().getDatasource(databaseName);
        if (datasource == null) {
            throw new IllegalArgumentException("Database not found: " + databaseName);
        }

        JsonArray columns = new JsonArray();

        try (Connection conn = datasource.getConnection()) {
            DatabaseMetaData metadata = conn.getMetaData();

            // Get columns
            try (ResultSet rs = metadata.getColumns(null, null, tableName, "%")) {
                while (rs.next()) {
                    JsonObject column = new JsonObject();
                    column.addProperty("name", rs.getString("COLUMN_NAME"));
                    column.addProperty("type", rs.getString("TYPE_NAME"));
                    column.addProperty("size", rs.getInt("COLUMN_SIZE"));
                    column.addProperty("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                    String defaultValue = rs.getString("COLUMN_DEF");
                    if (defaultValue != null) {
                        column.addProperty("default", defaultValue);
                    }
                    columns.add(column);
                }
            }

            // Get primary keys
            JsonArray primaryKeys = new JsonArray();
            try (ResultSet rs = metadata.getPrimaryKeys(null, null, tableName)) {
                while (rs.next()) {
                    primaryKeys.add(rs.getString("COLUMN_NAME"));
                }
            }

            JsonObject result = new JsonObject();
            result.addProperty("database", databaseName);
            result.addProperty("table_name", tableName);
            result.add("columns", columns);
            result.add("primary_keys", primaryKeys);

            return result;
        }
    }
}
