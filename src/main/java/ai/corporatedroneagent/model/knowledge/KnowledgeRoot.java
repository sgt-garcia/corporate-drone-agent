package ai.corporatedroneagent.model.knowledge;

import java.time.Instant;
import java.util.UUID;

public class KnowledgeRoot {

    private UUID id;
    private KnowledgeSource source;
    private String reference = "";
    private String displayName = "";
    private boolean paused;
    private String configJson = "";
    private long totalResources;
    private long totalSizeBytes;
    private WorkStatus scanStatus = WorkStatus.TO_DO;
    private Boolean scanSuccess;
    private String scanMessage = "";
    private Instant scanStartedAt;
    private Instant scanFinishedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public KnowledgeSource getSource() {
        return source;
    }

    public void setSource(KnowledgeSource source) {
        this.source = source;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference == null ? "" : reference;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? "" : displayName;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson == null ? "" : configJson;
    }

    public long getTotalResources() {
        return totalResources;
    }

    public void setTotalResources(long totalResources) {
        this.totalResources = Math.max(0, totalResources);
    }

    public long getTotalSizeBytes() {
        return totalSizeBytes;
    }

    public void setTotalSizeBytes(long totalSizeBytes) {
        this.totalSizeBytes = Math.max(0, totalSizeBytes);
    }

    public WorkStatus getScanStatus() {
        return scanStatus;
    }

    public void setScanStatus(WorkStatus scanStatus) {
        this.scanStatus = scanStatus == null ? WorkStatus.TO_DO : scanStatus;
    }

    public Boolean getScanSuccess() {
        return scanSuccess;
    }

    public void setScanSuccess(Boolean scanSuccess) {
        this.scanSuccess = scanSuccess;
    }

    public String getScanMessage() {
        return scanMessage;
    }

    public void setScanMessage(String scanMessage) {
        this.scanMessage = scanMessage == null ? "" : scanMessage;
    }

    public Instant getScanStartedAt() {
        return scanStartedAt;
    }

    public void setScanStartedAt(Instant scanStartedAt) {
        this.scanStartedAt = scanStartedAt;
    }

    public Instant getScanFinishedAt() {
        return scanFinishedAt;
    }

    public void setScanFinishedAt(Instant scanFinishedAt) {
        this.scanFinishedAt = scanFinishedAt;
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
