package com.iai.ignition.gateway.tools.database;

import com.iai.ignition.common.tools.IAITool;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.common.gson.JsonArray;
import com.inductiveautomation.ignition.common.gson.JsonObject;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.datasource.Datasource;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;

import java.util.Collection;

/**
 * Tool for listing available database connections.
 */
public class ListDatabasesTool implements IAITool {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.tools.database.ListDatabasesTool");

    private final GatewayContext gatewayContext;
    private final IAISettings settings;

    public ListDatabasesTool(GatewayContext gatewayContext, IAISettings settings) {
        this.gatewayContext = gatewayContext;
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "list_databases";
    }

    @Override
    public String getDescription() {
        return "List all available database connections in Ignition. Returns database connection names and status.";
    }

    @Override
    public JsonObject getParameterSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        schema.add("required", new JsonArray());
        return schema;
    }

    @Override
    public JsonObject execute(JsonObject params) throws Exception {
        logger.debug("Listing available databases");

        Collection<Datasource> datasources = gatewayContext.getDatasourceManager().getDatasources();

        JsonArray databases = new JsonArray();
        for (Datasource datasource : datasources) {
            JsonObject db = new JsonObject();
            db.addProperty("name", datasource.getName());

            // Test connection status
            boolean connected = false;
            String status = "unknown";
            try {
                java.sql.Connection conn = datasource.getConnection();
                if (conn != null) {
                    connected = !conn.isClosed();
                    status = connected ? "connected" : "disconnected";
                    conn.close();
                }
            } catch (Exception e) {
                status = "error: " + e.getMessage();
                logger.trace("Could not test connection for datasource: " + datasource.getName(), e);
            }
            db.addProperty("connected", connected);
            db.addProperty("status", status);

            databases.add(db);
        }

        JsonObject result = new JsonObject();
        result.add("databases", databases);
        result.addProperty("count", databases.size());

        return result;
    }
}
