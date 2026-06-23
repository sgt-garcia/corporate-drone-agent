package ai.corporatedroneagent.dto;

import java.util.UUID;

/**
 * A local folder the agent scans. The compact constructor normalizes blanks so the
 * UI never sees a null field.
 */
public record KnowledgeFolderDto(
        UUID id,
        String path,
        String status,
        long files,
        String size,
        String nextScan,
        String checked,
        String message
) {

    public KnowledgeFolderDto {
        path = path == null ? "" : path;
        status = status == null || status.isBlank() ? "scanned" : status;
        files = Math.max(0, files);
        size = size == null ? "" : size;
        nextScan = nextScan == null ? "" : nextScan;
        checked = checked == null ? "" : checked;
        message = message == null ? "" : message;
    }
}
