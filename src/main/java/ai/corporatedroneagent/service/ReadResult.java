package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgePipelineReason;
import java.time.Instant;

/**
 * The read stage outcome for one item, plus the authoritative resource metadata the
 * engine stores (a full fetch can refine what enumeration knew — e.g. a Jira issue's
 * true byte size). {@code reference} must match the manifest's so the upsert key is
 * stable. An item-level failure (unsupported format, too large, unreadable) is returned
 * as {@code success=false}; a root-level failure (auth/network) is thrown by the session.
 */
public record ReadResult(
        String reference,
        String displayName,
        String format,
        long sizeBytes,
        Instant lastModifiedAt,
        boolean success,
        KnowledgePipelineReason reason,
        String message,
        byte[] value
) {

    public static ReadResult of(
            String reference,
            String displayName,
            String format,
            long sizeBytes,
            Instant lastModifiedAt,
            byte[] value
    ) {
        return new ReadResult(reference, displayName, format, sizeBytes, lastModifiedAt, true, null, "", value);
    }

    public static ReadResult failed(
            String reference,
            String displayName,
            String format,
            long sizeBytes,
            Instant lastModifiedAt,
            KnowledgePipelineReason reason,
            String message
    ) {
        return new ReadResult(reference, displayName, format, sizeBytes, lastModifiedAt, false, reason, message, null);
    }
}
