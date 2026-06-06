package ai.corporatedroneagent.model.knowledge;

import java.time.Instant;
import java.util.UUID;

public class KnowledgeResourceIndex {

    private UUID id;
    private UUID chunkId;
    private WorkStatus status = WorkStatus.TO_DO;
    private Boolean success;
    private KnowledgePipelineReason reason;
    private String message = "";
    private String indexReference = "";
    private Instant indexedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getChunkId() {
        return chunkId;
    }

    public void setChunkId(UUID chunkId) {
        this.chunkId = chunkId;
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

    public KnowledgePipelineReason getReason() {
        return reason;
    }

    public void setReason(KnowledgePipelineReason reason) {
        this.reason = reason;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message == null ? "" : message;
    }

    public String getIndexReference() {
        return indexReference;
    }

    public void setIndexReference(String indexReference) {
        this.indexReference = indexReference == null ? "" : indexReference;
    }

    public Instant getIndexedAt() {
        return indexedAt;
    }

    public void setIndexedAt(Instant indexedAt) {
        this.indexedAt = indexedAt;
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
