package ai.corporatedroneagent.dto;

/**
 * A Jira project the agent scans. Mirrors a local knowledge folder: a capped,
 * continuously-scanned sub-scope with a status pill in the UI. Project
 * discovery uses Jira, but issue indexing is not implemented yet, so the live
 * "scanning" status is a client-side display only: persisted projects settle to
 * "scanned" or "paused".
 */
public class JiraProjectDto {

    private String id = "";
    private String key = "";
    private String name = "";
    private String status = "scanned";
    private long issues;
    private String checked = "";

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

    public long getIssues() {
        return issues;
    }

    public void setIssues(long issues) {
        this.issues = Math.max(0, issues);
    }

    public String getChecked() {
        return checked;
    }

    public void setChecked(String checked) {
        this.checked = checked == null ? "" : checked;
    }
}
