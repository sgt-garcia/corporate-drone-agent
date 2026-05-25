package ai.corporatedroneagent.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Conversation {

    private UUID id;
    private UUID projectId;
    private String name;
    private ConversationSettings settings = new ConversationSettings();
    private List<Message> messages = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ConversationSettings getSettings() {
        return settings;
    }

    public void setSettings(ConversationSettings settings) {
        this.settings = settings == null ? new ConversationSettings() : settings;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages == null ? new ArrayList<>() : messages;
    }
}
