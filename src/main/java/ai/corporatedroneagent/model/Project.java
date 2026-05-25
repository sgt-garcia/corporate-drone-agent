package ai.corporatedroneagent.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Project {

    private UUID id;
    private String name;
    private String workingFolder;
    private String customInstructions;
    private List<UUID> conversationIds = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWorkingFolder() {
        return workingFolder;
    }

    public void setWorkingFolder(String workingFolder) {
        this.workingFolder = workingFolder;
    }

    public String getCustomInstructions() {
        return customInstructions;
    }

    public void setCustomInstructions(String customInstructions) {
        this.customInstructions = customInstructions;
    }

    public List<UUID> getConversationIds() {
        return conversationIds;
    }

    public void setConversationIds(List<UUID> conversationIds) {
        this.conversationIds = conversationIds == null ? new ArrayList<>() : conversationIds;
    }
}
