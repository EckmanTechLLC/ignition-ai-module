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
 * Tool for listing tables in a database.
 */
public class ListTablesTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.database.ListTablesTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public ListTablesTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "list_tables";
    }

    @Override
    public String getDescription() {
        return "List all tables in a database. Returns table names and types.";
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

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("database");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String databaseName = params.get("database").getAsString();

        logger.debug("Listing tables for database: " + databaseName);

        Datasource datasource = gatewayContext.getDatasourceManager().getDatasource(databaseName);
        if (datasource == null) {
            throw new IllegalArgumentException("Database not found: " + databaseName);
        }

        JsonArray tables = new JsonArray();

        try (Connection conn = datasource.getConnection()) {
            DatabaseMetaData metadata = conn.getMetaData();
            try (ResultSet rs = metadata.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    JsonObject table = new JsonObject();
                    table.addProperty("name", rs.getString("TABLE_NAME"));
                    table.addProperty("type", rs.getString("TABLE_TYPE"));
                    tables.add(table);
                }
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("database", databaseName);
        result.add("tables", tables);
        result.addProperty("count", tables.size());

        return result;
    }
}
