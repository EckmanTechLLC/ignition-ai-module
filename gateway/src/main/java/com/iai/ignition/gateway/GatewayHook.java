package com.iai.ignition.gateway;

import com.iai.ignition.common.InsightChatComponent;
import com.iai.ignition.common.IgnitionAIModule;
import com.iai.ignition.gateway.database.ConversationSchemaManager;
// import com.iai.ignition.gateway.delegate.InsightChatModelDelegate;
import com.iai.ignition.gateway.endpoints.ConversationEndpoints;
import com.iai.ignition.gateway.records.IAISettings;
import com.iai.ignition.gateway.util.GatewayPathDetector;
import com.iai.ignition.gateway.web.IAISettingsPage;
import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.gateway.dataroutes.RouteGroup;
import com.inductiveautomation.ignition.gateway.localdb.persistence.IRecordListener;
import com.inductiveautomation.ignition.gateway.model.AbstractGatewayModuleHook;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.ignition.gateway.web.models.ConfigCategory;
import com.inductiveautomation.ignition.gateway.web.models.DefaultConfigTab;
import com.inductiveautomation.ignition.gateway.web.models.IConfigTab;
import com.inductiveautomation.ignition.gateway.web.models.KeyValue;
import com.inductiveautomation.perspective.common.api.ComponentRegistry;
// import com.inductiveautomation.perspective.gateway.api.ComponentModelDelegateRegistry;
import com.inductiveautomation.perspective.gateway.api.PerspectiveContext;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Gateway hook for the Ignition AI module.
 */
public class GatewayHook extends AbstractGatewayModuleHook {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.gateway.GatewayHook");

    private GatewayContext gatewayContext;
    private PerspectiveContext perspectiveContext;
    private ComponentRegistry componentRegistry;
    // private ComponentModelDelegateRegistry modelDelegateRegistry;
    private IAISettings settings;

    /**
     * Config category for the settings page
     */
    public static final ConfigCategory CONFIG_CATEGORY =
        new ConfigCategory("IgnitionAI", "IgnitionAI.nav.header", 700);

    @Override
    public List<ConfigCategory> getConfigCategories() {
        return Collections.singletonList(CONFIG_CATEGORY);
    }

    /**
     * Config tab for the settings page
     */
    public static final IConfigTab IAI_CONFIG_TAB = DefaultConfigTab.builder()
        .category(CONFIG_CATEGORY)
        .name("ignitionai-settings")
        .i18n("IgnitionAI.nav.settings.title")
        .page(IAISettingsPage.class)
        .terms("ignition ai settings claude")
        .build();

    @Override
    public List<? extends IConfigTab> getConfigPanels() {
        return Collections.singletonList(IAI_CONFIG_TAB);
    }

    @Override
    public void setup(GatewayContext context) {
        this.gatewayContext = context;
        logger.info("Setting up Ignition AI module.");

        // Register localization bundle
        BundleUtil.get().addBundle("IgnitionAI", getClass(), "IgnitionAI");

        // Register settings record schema
        try {
            context.getSchemaUpdater().updatePersistentRecords(IAISettings.META);
            logger.info("Settings schema registered.");
        } catch (SQLException e) {
            logger.error("Error registering settings schema", e);
        }

        // Create default settings record if needed
        maybeCreateSettings(context);

        // Add listener for settings changes
        IAISettings.META.addRecordListener(new IRecordListener<IAISettings>() {
            @Override
            public void recordUpdated(IAISettings record) {
                logger.info("Settings updated, attempting to create database tables.");
                createDatabaseTables(record);
            }

            @Override
            public void recordAdded(IAISettings record) {
                logger.info("Settings added.");
            }

            @Override
            public void recordDeleted(KeyValue keyValue) {
                logger.info("Settings deleted.");
            }
        });
    }

    /**
     * Create default settings record if it doesn't exist.
     */
    private void maybeCreateSettings(GatewayContext context) {
        logger.trace("Attempting to create IAI Settings Record");
        try {
            IAISettings settingsRecord = context.getLocalPersistenceInterface().createNew(IAISettings.META);
            settingsRecord.setId(0L);
            settingsRecord.setApiKey("");
            settingsRecord.setModelName("claude-sonnet-4-5-20250929");
            settingsRecord.setSystemPrompt(getDefaultSystemPrompt());
            settingsRecord.setDatabaseConnection("");
            settingsRecord.setEnableDatabaseTools(true);
            settingsRecord.setMaxToolResultSizeKB(100);
            settingsRecord.setMaxTagHistoryRecords(1000);
            settingsRecord.setMaxAlarmHistoryRecords(1000);
            settingsRecord.setQueryTimeoutSeconds(30);
            settingsRecord.setMaxConversationHistoryMessages(50);
            settingsRecord.setGatewayDataPath("");

            // This doesn't override existing settings, only creates if doesn't exist
            context.getSchemaUpdater().ensureRecordExists(settingsRecord);
            logger.info("IAI Settings Record established.");
        } catch (Exception e) {
            logger.error("Failed to establish IAI Settings Record", e);
        }
    }

    /**
     * Get default system prompt for Claude.
     */
    private String getDefaultSystemPrompt() {
        return "You are Ignition AI, an AI assistant integrated into Inductive Automation's Ignition SCADA platform.\n\n" +
            "## Your Role\n" +
            "You help users understand, explore, and explain existing Ignition systems. You provide read-only analysis " +
            "and never modify configurations, tags, or code.\n\n" +
            "## Available Tools\n" +
            "You have access to tools for reading project resources, querying tags and alarms, and analyzing system data.\n\n" +
            "## Response Style\n" +
            "Be clear, concise, and technical. Cite specific file paths and line numbers when referencing code.";
    }

    @Override
    public void startup(LicenseState activationState) {
        logger.info("Starting up Ignition AI module!");

        // Load settings
        loadSettings();

        // Detect and update gateway data path if needed
        detectAndUpdateGatewayPath();

        // Create database tables if database connection is configured
        if (settings != null) {
            createDatabaseTables(settings);
        }

        this.perspectiveContext = PerspectiveContext.get(this.gatewayContext);
        this.componentRegistry = this.perspectiveContext.getComponentRegistry();
        // this.modelDelegateRegistry = this.perspectiveContext.getComponentModelDelegateRegistry();

        if (this.componentRegistry != null) {
            logger.info("Registering Insight Chat component.");
            this.componentRegistry.registerComponent(InsightChatComponent.DESCRIPTOR);
        } else {
            logger.error("Reference to component registry not found, Ignition AI module will fail to function!");
        }

        // Model delegate not needed - using HTTP POST endpoints instead
        // if (this.modelDelegateRegistry != null) {
        //     logger.info("Registering Insight Chat model delegate.");
        //     this.modelDelegateRegistry.register(InsightChatComponent.COMPONENT_ID, InsightChatModelDelegate::new);
        // } else {
        //     logger.error("ModelDelegateRegistry was not found!");
        // }
    }

    /**
     * Load settings from internal database.
     */
    private void loadSettings() {
        try {
            this.settings = gatewayContext.getLocalPersistenceInterface()
                .find(IAISettings.META, 0L);

            if (this.settings != null) {
                logger.info("Settings loaded successfully.");
                logger.debug("API Key configured: " + (this.settings.getApiKey() != null && !this.settings.getApiKey().isEmpty()));
                logger.debug("Database connection: " + this.settings.getDatabaseConnection());
            } else {
                logger.warn("No settings found in database.");
            }
        } catch (Exception e) {
            logger.error("Error loading settings", e);
        }
    }

    /**
     * Detect gateway data path and update settings if empty.
     */
    private void detectAndUpdateGatewayPath() {
        if (settings == null) {
            logger.warn("Settings not loaded, cannot detect gateway path.");
            return;
        }

        String currentPath = settings.getGatewayDataPath();
        Optional<String> detectedPath = GatewayPathDetector.detectGatewayDataPath(currentPath);

        if (detectedPath.isPresent()) {
            String path = detectedPath.get();

            // Update settings if path is empty or different
            if (currentPath == null || currentPath.isEmpty() || !currentPath.equals(path)) {
                try {
                    settings.setGatewayDataPath(path);
                    gatewayContext.getLocalPersistenceInterface().save(settings);
                    logger.info("Gateway data path auto-detected and saved: " + path);
                } catch (Exception e) {
                    logger.error("Failed to save detected gateway path", e);
                }
            }

            // Verify the path is valid
            if (GatewayPathDetector.verifyGatewayDataPath(path)) {
                logger.info("Gateway data path verified successfully: " + path);
            } else {
                logger.warn("Detected gateway data path could not be verified: " + path);
            }
        } else {
            logger.warn("Could not auto-detect gateway data path. Please configure manually in settings.");
        }
    }

    /**
     * Create database tables for conversations and messages.
     */
    private void createDatabaseTables(IAISettings settings) {
        String dbConnection = settings.getDatabaseConnection();
        if (dbConnection == null || dbConnection.isEmpty()) {
            logger.debug("Database connection not configured, skipping table creation.");
            return;
        }

        logger.info("Attempting to create IAI database tables in connection: " + dbConnection);
        boolean success = ConversationSchemaManager.createTables(
            gatewayContext.getDatasourceManager(),
            dbConnection
        );

        if (success) {
            logger.info("Database tables created successfully.");
        } else {
            logger.error("Failed to create database tables. Check database connection configuration.");
        }
    }

    /**
     * Get current module settings.
     */
    public IAISettings getSettings() {
        return settings;
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down Ignition AI module and removing registered components.");

        // Remove localization bundle
        BundleUtil.get().removeBundle("IgnitionAI");

        if (this.componentRegistry != null) {
            this.componentRegistry.removeComponent(InsightChatComponent.COMPONENT_ID);
        } else {
            logger.warn("Component registry was null, could not unregister Insight Chat component.");
        }

        // Model delegate not used
        // if (this.modelDelegateRegistry != null) {
        //     this.modelDelegateRegistry.remove(InsightChatComponent.COMPONENT_ID);
        // }
    }

    @Override
    public Optional<String> getMountedResourceFolder() {
        return Optional.of("mounted");
    }

    @Override
    public Optional<String> getMountPathAlias() {
        return Optional.of(IgnitionAIModule.URL_ALIAS);
    }

    @Override
    public boolean isFreeModule() {
        return true;
    }

    @Override
    public void mountRouteHandlers(RouteGroup routes) {
        logger.info("Mounting RPC endpoints for Perspective components");
        ConversationEndpoints.mountRoutes(routes);
    }
}
