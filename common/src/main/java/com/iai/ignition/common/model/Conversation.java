package com.iai.ignition.common.model;

import java.io.Serializable;

/**
 * Represents a conversation between a user and the AI assistant.
 * Corresponds to the iai_conversations database table.
 */
public class Conversation implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String userName;
    private String projectName;
    private String title;
    private long createdAt;
    private long lastUpdatedAt;

    /**
     * No-arg constructor for serialization.
     */
    public Conversation() {
    }

    /**
     * Full constructor for creating new conversations.
     */
    public Conversation(String id, String userName, String projectName, String title, long createdAt, long lastUpdatedAt) {
        this.id = id;
        this.userName = userName;
        this.projectName = projectName;
        this.title = title;
        this.createdAt = createdAt;
        this.lastUpdatedAt = lastUpdatedAt;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(long lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        Conversation that = (Conversation) obj;

        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
