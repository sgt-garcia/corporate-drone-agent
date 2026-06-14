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

    /** Full content + authoritative metadata for one item the engine has decided to (re)index. */
    FetchedResource fetch(ResourceManifest manifest);

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
