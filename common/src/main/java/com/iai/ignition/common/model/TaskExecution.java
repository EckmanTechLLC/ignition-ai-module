package com.iai.ignition.common.model;

import java.io.Serializable;

/**
 * Represents a single execution of a scheduled task.
 * Corresponds to the iai_task_executions database table.
 */
public class TaskExecution implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String taskId;
    private long executedAt;
    private String conversationId;
    private String status;
    private String errorMessage;
    private Integer executionTimeMs;

    /**
     * No-arg constructor for serialization.
     */
    public TaskExecution() {
    }

    /**
     * Full constructor for creating new task executions.
     */
    public TaskExecution(String id, String taskId, long executedAt, String conversationId,
                         String status, String errorMessage, Integer executionTimeMs) {
        this.id = id;
        this.taskId = taskId;
        this.executedAt = executedAt;
        this.conversationId = conversationId;
        this.status = status;
        this.errorMessage = errorMessage;
        this.executionTimeMs = executionTimeMs;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public long getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(long executedAt) {
        this.executedAt = executedAt;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(Integer executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        TaskExecution that = (TaskExecution) obj;

        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
