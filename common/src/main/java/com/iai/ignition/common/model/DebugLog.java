package com.iai.ignition.common.model;

/**
 * Debug log entry containing full request/response JSON from LLM API calls.
 * Used for transparency and debugging.
 */
public class DebugLog {
    private String id;
    private String messageId;
    private String requestJson;
    private String responseJson;
    private long timestamp;

    public DebugLog() {
    }

    public DebugLog(String id, String messageId, String requestJson, String responseJson, long timestamp) {
        this.id = id;
        this.messageId = messageId;
        this.requestJson = requestJson;
        this.responseJson = responseJson;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getRequestJson() {
        return requestJson;
    }

    public void setRequestJson(String requestJson) {
        this.requestJson = requestJson;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public void setResponseJson(String responseJson) {
        this.responseJson = responseJson;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
