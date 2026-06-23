package ai.corporatedroneagent.dto;

/**
 * A Jira project the agent scans. Mirrors a local knowledge folder: a capped,
 * explicitly scanned sub-scope with a status pill in the UI. Project discovery
 * uses Jira, while issue indexing runs through the shared knowledge pipeline.
 * The compact constructor normalizes blanks so the UI never sees a null field.
 */
public record JiraProjectDto(
        String id,
        String key,
        String name,
        String status,
        long issues,
        String checked,
        String message
) {

    public JiraProjectDto {
        id = id == null ? "" : id;
        key = key == null ? "" : key;
        name = name == null ? "" : name;
        status = status == null || status.isBlank() ? "scanned" : status;
        issues = Math.max(0, issues);
        checked = checked == null ? "" : checked;
        message = message == null ? "" : message;
    }
}
