package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.WorkStatus;
import ai.corporatedroneagent.util.Strings;
import java.time.Duration;
import java.time.Instant;

/**
 * Shared, source-agnostic formatting for a {@link KnowledgeRoot} as surfaced to the Settings UI.
 * Used by the local-folder, Jira-project, and Confluence-space DTO mappers so the three surfaces
 * present status and freshness identically and can't drift. Kept neutral (depending on nothing in
 * the settings verticals) so those verticals can live in separate beans without a dependency cycle.
 */
final class KnowledgeRootFormatting {

    private KnowledgeRootFormatting() {
    }

    // Single status precedence shared by local folders, Jira projects, and Confluence spaces:
    // paused beats an in-progress scan, a settled failure surfaces as an error (reason in
    // scanMessage), everything else is scanned.
    static String status(KnowledgeRoot root) {
        if (root.isPaused()) {
            return "paused";
        }
        if (root.getScanStatus() == WorkStatus.IN_PROGRESS) {
            return "scanning";
        }
        if (Boolean.FALSE.equals(root.getScanSuccess())) {
            return "error";
        }
        return "scanned";
    }

    // Human-readable "checked …" freshness from the last successful scan's finish time. Empty
    // while scanning/paused or before a first successful scan, so the UI only shows it on a
    // settled "scanned" row.
    static String checkedLabel(KnowledgeRoot root) {
        if (root.isPaused()
                || root.getScanStatus() != WorkStatus.DONE
                || !Boolean.TRUE.equals(root.getScanSuccess())
                || root.getScanFinishedAt() == null) {
            return "";
        }
        return relativeTime(root.getScanFinishedAt(), Instant.now());
    }

    static String relativeTime(Instant then, Instant now) {
        long seconds = Math.max(0, Duration.between(then, now).getSeconds());
        if (seconds < 45) {
            return "just now";
        }
        long minutes = Math.round(seconds / 60.0);
        if (minutes < 60) {
            return minutes + " min ago";
        }
        long hours = Math.round(minutes / 60.0);
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }
        long days = Math.round(hours / 24.0);
        return days + (days == 1 ? " day ago" : " days ago");
    }

    // "KEY - Name" display label for a Jira project / Confluence space, tolerating a blank side.
    static String keyAndName(String key, String name) {
        String trimmedKey = Strings.defaultIfBlank(key, "");
        String trimmedName = Strings.defaultIfBlank(name, "");
        if (trimmedKey.isBlank()) {
            return trimmedName;
        }
        if (trimmedName.isBlank()) {
            return trimmedKey;
        }
        return trimmedKey + " - " + trimmedName;
    }
}
