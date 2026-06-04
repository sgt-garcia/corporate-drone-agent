package ai.corporatedroneagent.model.knowledge;

import java.time.Instant;
import java.util.UUID;

public class KnowledgeResourceRead {

    private UUID id;
    private UUID resourceId;
    private WorkStatus status = WorkStatus.TO_DO;
    private Boolean success;
    private String message = "";
    private byte[] value;
    private Instant readAt;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public void setResourceId(UUID resourceId) {
        this.resourceId = resourceId;
    }

    public WorkStatus getStatus() {
        return status;
    }

    public void setStatus(WorkStatus status) {
        this.status = status == null ? WorkStatus.TO_DO : status;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message == null ? "" : message;
    }

    public byte[] getValue() {
        return value == null ? null : value.clone();
    }

    public void setValue(byte[] value) {
        this.value = value == null ? null : value.clone();
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
