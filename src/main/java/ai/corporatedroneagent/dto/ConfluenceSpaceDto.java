package ai.corporatedroneagent.dto;

/**
 * A Confluence space the agent scans. Mirrors a Jira project: a capped, explicitly
 * scanned sub-scope with a status pill in the UI. Space discovery uses Confluence,
 * while page indexing runs through the shared knowledge pipeline.
 */
public class ConfluenceSpaceDto {

    private String id = "";
    private String key = "";
    private String name = "";
    private String status = "scanned";
    private long pages;
    private String checked = "";
    private String message = "";

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id == null ? "" : id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key == null ? "" : key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status == null || status.isBlank() ? "scanned" : status;
    }

    public long getPages() {
        return pages;
    }

    public void setPages(long pages) {
        this.pages = Math.max(0, pages);
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
