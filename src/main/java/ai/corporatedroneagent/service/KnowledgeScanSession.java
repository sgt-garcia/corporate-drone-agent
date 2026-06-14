package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import java.util.List;

/**
 * A prepared scan of one knowledge root, holding the source's resolved config and
 * credentials. Created by {@link KnowledgeSourceAdapter#openSession}. The generic
 * {@link KnowledgeScanEngine} drives it: enumerate → (per changed item) fetch.
 */
public interface KnowledgeScanSession extends AutoCloseable {

    /** Cheap listing of the source's items — no content download. */
    List<ResourceManifest> enumerate(ScanCursor cursor);

    /**
     * Acquire one item's bytes + authoritative metadata. Return {@code success=false} for an
     * item-level skip (unsupported/too large/unreadable); throw
     * {@link org.springframework.web.server.ResponseStatusException} for a root-level failure
     * (auth/network) so the engine aborts the whole scan.
     */
    ReadResult read(ResourceManifest manifest);

    /** Render the read bytes to text. Only called when the read succeeded. */
    ConversionResult convert(ReadResult read);

    /** Source-specific change detection against the stored resource (timestamp, size, hash…). */
    boolean isUnchanged(KnowledgeResource existing, ResourceManifest manifest);

    /**
     * Whether items missing from this enumeration should be deleted. Full enumerations
     * (folders) return true; cursor/delta enumerations (Jira incremental) return false,
     * since they only ever see changed items.
     */
    boolean reconcilesDeletes();

    @Override
    default void close() {
    }
}
