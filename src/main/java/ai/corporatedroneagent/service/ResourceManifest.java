package ai.corporatedroneagent.service;

import java.time.Instant;

/**
 * Lightweight metadata for one item in a knowledge source, produced by an adapter's
 * enumeration step — cheap, with no content download. The engine uses it to decide
 * whether the item changed; {@code handle} is adapter-private data (e.g. a Jira issue)
 * passed back to {@link KnowledgeScanSession#fetchContent}.
 */
public record ResourceManifest(
        String reference,
        String displayName,
        String format,
        long sizeBytes,
        Instant lastModifiedAt,
        Object handle
) {
}
