package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;

/**
 * Resolves the Confluence connection + token for a scan. Abstracted so
 * {@link ConfluenceSourceAdapter} stays decoupled from where the connection lives
 * (SettingsService in production, a stub in tests).
 */
public interface ConfluenceConnectionResolver {

    /**
     * @throws org.springframework.web.server.ResponseStatusException if Confluence isn't set up.
     */
    ConfluenceConnection resolve(KnowledgeRoot root);
}
