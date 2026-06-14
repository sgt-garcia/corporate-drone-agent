package ai.corporatedroneagent.service;

import java.time.Instant;

/**
 * The authoritative metadata for an item after it is fetched, plus its read/conversion
 * {@link ResourceContent}. The engine builds the stored {@code KnowledgeResource} from
 * this — not from the enumeration manifest — because a full fetch can refine the
 * metadata (e.g. a Jira issue's true byte size is only known once the document is read).
 * The {@code reference} must equal the manifest's so the upsert key stays stable.
 */
public record FetchedResource(
        String reference,
        String displayName,
        String format,
        long sizeBytes,
        Instant lastModifiedAt,
        ResourceContent content
) {
}
