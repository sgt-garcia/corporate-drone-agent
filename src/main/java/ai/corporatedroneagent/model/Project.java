package ai.corporatedroneagent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Project {

    private UUID id;
    private String name;
    private String workingFolder;
    private String customInstructions;
    private Instant createdAt;
    private List<UUID> conversationIds = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
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
