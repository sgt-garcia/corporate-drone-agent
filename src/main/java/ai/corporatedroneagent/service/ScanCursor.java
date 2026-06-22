package ai.corporatedroneagent.service;

import java.time.Instant;

/**
 * An opaque incremental hint the engine persists in {@code KnowledgeRoot.configJson}
 * between scans and hands to the adapter's enumeration. Sources that support
 * server-side delta filtering (e.g. Jira "updated since") use it; sources that can't
 * ignore it and rely on the engine's per-item change check instead.
 */
public record ScanCursor(Instant updatedSince) {

    public static ScanCursor none() {
        return new ScanCursor(null);
    }
}
