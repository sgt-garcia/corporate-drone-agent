package ai.corporatedroneagent.dto;

/**
 * A Confluence space the agent scans. Mirrors a Jira project: a capped, explicitly
 * scanned sub-scope with a status pill in the UI. Space discovery uses Confluence,
 * while page indexing runs through the shared knowledge pipeline. The compact
 * constructor normalizes blanks so the UI never sees a null field.
 */
public record ConfluenceSpaceDto(
        String id,
        String key,
        String name,
        String status,
        long pages,
        String checked,
        String message
) {

    public ConfluenceSpaceDto {
        id = id == null ? "" : id;
        key = key == null ? "" : key;
        name = name == null ? "" : name;
        status = status == null || status.isBlank() ? "scanned" : status;
        pages = Math.max(0, pages);
        checked = checked == null ? "" : checked;
        message = message == null ? "" : message;
    }
}
