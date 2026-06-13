package ai.corporatedroneagent.dto;

import java.util.UUID;

public class KnowledgeFolderDto {

    private UUID id;
    private String path = "";
    private String status = "scanned";
    private long files;
    private String size = "";
    private String nextScan = "";
    private String checked = "";
    private String message = "";

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path == null ? "" : path;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status == null || status.isBlank() ? "scanned" : status;
    }

    public long getFiles() {
        return files;
    }

    public void setFiles(long files) {
        this.files = Math.max(0, files);
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size == null ? "" : size;
    }

    public String getNextScan() {
        return nextScan;
    }

    public void setNextScan(String nextScan) {
        this.nextScan = nextScan == null ? "" : nextScan;
    }

    public String getChecked() {
        return checked;
    }

    public void setChecked(String checked) {
        this.checked = checked == null ? "" : checked;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message == null ? "" : message;
    }
}
