package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;

/**
 * Resolves the Jira connection + token for a scan. Abstracted so {@link JiraSourceAdapter}
 * stays decoupled from where the connection lives (SettingsService in production, a stub in
 * tests).
 */
public interface JiraConnectionResolver {

    /**
     * @throws org.springframework.web.server.ResponseStatusException if Jira isn't set up.
     */
    JiraConnection resolve(KnowledgeRoot root);
}
