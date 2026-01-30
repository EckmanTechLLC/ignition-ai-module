package com.iai.ignition.gateway.tools.database;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.datasource.Datasource;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.sql.*;
import java.util.regex.Pattern;

/**
 * Tool for executing ad-hoc SQL SELECT queries.
 * Validates that only SELECT statements are executed for safety.
 */
public class ExecuteSqlQueryTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.database.ExecuteSqlQueryTool");
    private static final Pattern SELECT_PATTERN = Pattern.compile("^\\s*SELECT\\s+", Pattern.CASE_INSENSITIVE);

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public ExecuteSqlQueryTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "execute_sql_query";
    }

    @Override
    public String getDescription() {
        return "Execute an ad-hoc SQL SELECT query. Only SELECT statements are allowed for safety. " +
                "Returns query results. Enforces queryTimeoutSeconds setting.";
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

        JsonObject query = new JsonObject();
        query.addProperty("type", "string");
        query.addProperty("description", "The SQL SELECT query to execute");
        properties.add("query", query);

        schema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("database");
        required.add("query");
        schema.add("required", required);

        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        String databaseName = params.get("database").getAsString();
        String query = params.get("query").getAsString();

        logger.debug("Executing SQL query on database: " + databaseName);

        // Validate that query is a SELECT statement
        if (!SELECT_PATTERN.matcher(query).find()) {
            throw new IllegalArgumentException("Only SELECT queries are allowed. Query must start with SELECT.");
        }

        Datasource datasource = gatewayContext.getDatasourceManager().getDatasource(databaseName);
        if (datasource == null) {
            throw new IllegalArgumentException("Database not found: " + databaseName);
        }

        JsonArray rows = new JsonArray();

        try (Connection conn = datasource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.setQueryTimeout(settings.getQueryTimeoutSeconds());

            try (ResultSet rs = stmt.executeQuery(query)) {
                ResultSetMetaData rsmd = rs.getMetaData();
                int columnCount = rsmd.getColumnCount();

                // Build column info
                JsonArray columns = new JsonArray();
                for (int i = 1; i <= columnCount; i++) {
                    JsonObject col = new JsonObject();
                    col.addProperty("name", rsmd.getColumnName(i));
                    col.addProperty("type", rsmd.getColumnTypeName(i));
                    columns.add(col);
                }

                // Build rows
                int rowCount = 0;
                while (rs.next() && rowCount < 1000) { // Limit to 1000 rows
                    JsonObject row = new JsonObject();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = rsmd.getColumnName(i);
                        Object value = rs.getObject(i);
                        if (value != null) {
                            row.addProperty(columnName, value.toString());
                        }
                    }
                    rows.add(row);
                    rowCount++;
                }

                JsonObject result = new JsonObject();
                result.addProperty("database", databaseName);
                result.add("columns", columns);
                result.add("rows", rows);
                result.addProperty("count", rows.size());

                if (rowCount >= 1000) {
                    result.addProperty("truncated", true);
                    result.addProperty("message", "Results limited to 1000 rows");
                }

                return result;
            }
        }
    }
}
