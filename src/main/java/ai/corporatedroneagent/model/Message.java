package ai.corporatedroneagent.model;

import ai.corporatedroneagent.dto.MessageDto;
import ai.corporatedroneagent.dto.MessageSourceDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Message {

    private UUID id;
    private String role;
    private String content;
    private Instant createdAt;
    private List<MessageSourceDto> sources = new ArrayList<>();

    public Message() {
    }

    public Message(UUID id, String role, String content, Instant createdAt) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<MessageSourceDto> getSources() {
        return sources;
    }

    public void setSources(List<MessageSourceDto> sources) {
        this.sources = sources == null ? new ArrayList<>() : sources;
    }

    public MessageDto toDto() {
        return new MessageDto(id, role, content, createdAt, sources);
    }
}
