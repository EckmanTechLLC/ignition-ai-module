package com.iai.ignition.gateway.records;

import com.inductiveautomation.ignition.gateway.localdb.persistence.*;
import simpleorm.dataset.SFieldFlags;

/**
 * Settings record for Ignition AI module.
 * Stores configuration for Claude API, database connection, and tool limits.
 */
public class IAISettings extends PersistentRecord {

    public static final RecordMeta<IAISettings> META = new RecordMeta<>(
        IAISettings.class,
        "IAISettings"
    ).setNounKey("IAISettings.Noun").setNounPluralKey("IAISettings.Noun.Plural");

    // Identity
    public static final IdentityField Id = new IdentityField(META);

    // Claude Configuration
    public static final StringField ApiKey = new StringField(META, "ApiKey", SFieldFlags.SMANDATORY);
    public static final StringField ModelName = new StringField(META, "ModelName").setDefault("claude-sonnet-4-5-20250929");
    public static final StringField SystemPrompt = new StringField(META, "SystemPrompt", SFieldFlags.SDESCRIPTIVE);

    // Database Configuration
    public static final StringField DatabaseConnection = new StringField(META, "DatabaseConnection", SFieldFlags.SMANDATORY);
    public static final BooleanField EnableDatabaseTools = new BooleanField(META, "EnableDatabaseTools").setDefault(true);

    // Tool Limits
    public static final IntField MaxToolResultSizeKB = new IntField(META, "MaxToolResultSizeKB").setDefault(100);
    public static final IntField MaxTagHistoryRecords = new IntField(META, "MaxTagHistoryRecords").setDefault(1000);
    public static final IntField MaxAlarmHistoryRecords = new IntField(META, "MaxAlarmHistoryRecords").setDefault(1000);
    public static final IntField QueryTimeoutSeconds = new IntField(META, "QueryTimeoutSeconds").setDefault(30);

    // Conversation Settings
    public static final IntField MaxConversationHistoryMessages = new IntField(META, "MaxConversationHistoryMessages").setDefault(50);

    // Gateway Detection
    public static final StringField GatewayDataPath = new StringField(META, "GatewayDataPath");

    // System Function Execution Settings
    public static final BooleanField AllowSystemFunctionExecution = new BooleanField(META, "AllowSystemFunctionExecution").setDefault(false);
    public static final StringField SystemFunctionMode = new StringField(META, "SystemFunctionMode").setDefault("READ_ONLY");
    public static final IntField SystemFunctionTimeoutSeconds = new IntField(META, "SystemFunctionTimeoutSeconds").setDefault(30);
    public static final IntField MaxSystemFunctionResultSizeKB = new IntField(META, "MaxSystemFunctionResultSizeKB").setDefault(100);

    // Categories for settings page organization
    static final Category ClaudeConfig = new Category("IAISettings.Category.Claude", 1000)
        .include(ApiKey, ModelName, SystemPrompt);
    static final Category DatabaseConfig = new Category("IAISettings.Category.Database", 1001)
        .include(DatabaseConnection, EnableDatabaseTools);
    static final Category ToolLimits = new Category("IAISettings.Category.ToolLimits", 1002)
        .include(MaxToolResultSizeKB, MaxTagHistoryRecords, MaxAlarmHistoryRecords, QueryTimeoutSeconds);
    static final Category ConversationSettings = new Category("IAISettings.Category.Conversation", 1003)
        .include(MaxConversationHistoryMessages);
    static final Category GatewaySettings = new Category("IAISettings.Category.Gateway", 1004)
        .include(GatewayDataPath);
    static final Category SystemFunctionSettings = new Category("IAISettings.Category.SystemFunctions", 1005)
        .include(AllowSystemFunctionExecution, SystemFunctionMode, SystemFunctionTimeoutSeconds, MaxSystemFunctionResultSizeKB);

    @Override
    public RecordMeta<?> getMeta() {
        return META;
    }

    // Getters
    public Long getId() {
        return getLong(Id);
    }

    public String getApiKey() {
        return getString(ApiKey);
    }

    public String getModelName() {
        return getString(ModelName);
    }

    public String getSystemPrompt() {
        return getString(SystemPrompt);
    }

    public String getDatabaseConnection() {
        return getString(DatabaseConnection);
    }

    public Boolean getEnableDatabaseTools() {
        return getBoolean(EnableDatabaseTools);
    }

    public Integer getMaxToolResultSizeKB() {
        return getInt(MaxToolResultSizeKB);
    }

    public Integer getMaxTagHistoryRecords() {
        return getInt(MaxTagHistoryRecords);
    }

    public Integer getMaxAlarmHistoryRecords() {
        return getInt(MaxAlarmHistoryRecords);
    }

    public Integer getQueryTimeoutSeconds() {
        return getInt(QueryTimeoutSeconds);
    }

    public Integer getMaxConversationHistoryMessages() {
        return getInt(MaxConversationHistoryMessages);
    }

    public String getGatewayDataPath() {
        return getString(GatewayDataPath);
    }

    // Setters
    public void setId(Long value) {
        setLong(Id, value);
    }

    public void setApiKey(String value) {
        setString(ApiKey, value);
    }

    public void setModelName(String value) {
        setString(ModelName, value);
    }

    public void setSystemPrompt(String value) {
        setString(SystemPrompt, value);
    }

    public void setDatabaseConnection(String value) {
        setString(DatabaseConnection, value);
    }

    public void setEnableDatabaseTools(Boolean value) {
        setBoolean(EnableDatabaseTools, value);
    }

    public void setMaxToolResultSizeKB(Integer value) {
        setInt(MaxToolResultSizeKB, value);
    }

    public void setMaxTagHistoryRecords(Integer value) {
        setInt(MaxTagHistoryRecords, value);
    }

    public void setMaxAlarmHistoryRecords(Integer value) {
        setInt(MaxAlarmHistoryRecords, value);
    }

    public void setQueryTimeoutSeconds(Integer value) {
        setInt(QueryTimeoutSeconds, value);
    }

    public void setMaxConversationHistoryMessages(Integer value) {
        setInt(MaxConversationHistoryMessages, value);
    }

    public void setGatewayDataPath(String value) {
        setString(GatewayDataPath, value);
    }

    public Boolean getAllowSystemFunctionExecution() {
        return getBoolean(AllowSystemFunctionExecution);
    }

    public void setAllowSystemFunctionExecution(Boolean value) {
        setBoolean(AllowSystemFunctionExecution, value);
    }

    public String getSystemFunctionMode() {
        return getString(SystemFunctionMode);
    }

    public void setSystemFunctionMode(String value) {
        setString(SystemFunctionMode, value);
    }

    public Integer getSystemFunctionTimeoutSeconds() {
        return getInt(SystemFunctionTimeoutSeconds);
    }

    public void setSystemFunctionTimeoutSeconds(Integer value) {
        setInt(SystemFunctionTimeoutSeconds, value);
    }

    public Integer getMaxSystemFunctionResultSizeKB() {
        return getInt(MaxSystemFunctionResultSizeKB);
    }

    public void setMaxSystemFunctionResultSizeKB(Integer value) {
        setInt(MaxSystemFunctionResultSizeKB, value);
    }
}
