package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.JiraSettings;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Resolves the Jira connection from the saved settings + secret store. */
@Service
public class SettingsJiraConnectionResolver implements JiraConnectionResolver {

    private final SettingsService settingsService;

    public SettingsJiraConnectionResolver(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public JiraConnection resolve(KnowledgeRoot root) {
        JiraSettings jira = settingsService.getJiraSettings();
        if (!jira.isConnected() || !jira.isTokenConfigured()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Save Jira setup before managing projects");
        }
        return new JiraConnection(
                jira.getInstanceUrl(),
                jira.getEmail(),
                jira.getApiVersion(),
                settingsService.savedJiraToken());
    }
}
