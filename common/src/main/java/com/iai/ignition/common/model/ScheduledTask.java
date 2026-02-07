package com.iai.ignition.common.model;

import java.io.Serializable;

/**
 * Represents a scheduled task that executes AI queries on a recurring schedule.
 * Corresponds to the iai_scheduled_tasks database table.
 */
public class ScheduledTask implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String userName;
    private String projectName;
    private String taskDescription;
    private String conversationId;
    private String prompt;
    private String cronExpression;
    private Long lastRunAt;
    private long nextRunAt;
    private String status;
    private String resultStorage;
    private long createdAt;
    private boolean enabled;

    /**
     * No-arg constructor for serialization.
     */
    public ScheduledTask() {
    }

    /**
     * Full constructor for creating new scheduled tasks.
     */
    public ScheduledTask(String id, String userName, String projectName, String taskDescription,
                         String conversationId, String prompt, String cronExpression,
                         Long lastRunAt, long nextRunAt, String status, String resultStorage,
                         long createdAt, boolean enabled) {
        this.id = id;
        this.userName = userName;
        this.projectName = projectName;
        this.taskDescription = taskDescription;
        this.conversationId = conversationId;
        this.prompt = prompt;
        this.cronExpression = cronExpression;
        this.lastRunAt = lastRunAt;
        this.nextRunAt = nextRunAt;
        this.status = status;
        this.resultStorage = resultStorage;
        this.createdAt = createdAt;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getTaskDescription() {
        return taskDescription;
    }

    public void setTaskDescription(String taskDescription) {
        this.taskDescription = taskDescription;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public Long getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Long lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public long getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(long nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResultStorage() {
        return resultStorage;
    }

    public void setResultStorage(String resultStorage) {
        this.resultStorage = resultStorage;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        ScheduledTask that = (ScheduledTask) obj;

        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
