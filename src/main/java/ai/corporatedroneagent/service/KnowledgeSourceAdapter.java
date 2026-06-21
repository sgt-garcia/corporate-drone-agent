package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import ai.corporatedroneagent.model.knowledge.KnowledgeSource;

/**
 * The only per-source code in the ingestion framework. One bean per
 * {@link KnowledgeSource}, discovered by the registry. It knows the source's API,
 * auth, and item shape; it knows nothing about chunking, indexing, the database,
 * status, or events — that all lives in the generic {@link KnowledgeScanEngine}.
 */
public interface KnowledgeSourceAdapter {

    KnowledgeSource source();

    /**
     * Resolve this root's config/credentials and open a scan session, or throw
     * {@link org.springframework.web.server.ResponseStatusException} if the source
     * isn't scannable (e.g. missing setup).
     */
    KnowledgeScanSession openSession(KnowledgeRoot root);

    /**
     * The id the UI rows key the scan-progress ticker on. A local folder row keys on
     * the root id, but a Jira project / Confluence space row keys on the external
     * project/space id its settings DTO carries — not the root id — so the live
     * "scanning X" name only reaches the right row when the source picks the id.
     */
    String scanProgressId(KnowledgeRoot root);
}
