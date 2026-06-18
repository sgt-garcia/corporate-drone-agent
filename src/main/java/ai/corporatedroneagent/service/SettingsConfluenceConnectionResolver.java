package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.ConfluenceSettings;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Resolves the Confluence connection from the saved settings + secret store. */
@Service
public class SettingsConfluenceConnectionResolver implements ConfluenceConnectionResolver {

    private final SettingsService settingsService;

    public SettingsConfluenceConnectionResolver(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public ConfluenceConnection resolve(KnowledgeRoot root) {
        ConfluenceSettings confluence = settingsService.getConfluenceSettings();
        if (!confluence.isConnected() || !confluence.isTokenConfigured()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Save Confluence setup before managing spaces");
        }
        return new ConfluenceConnection(
                confluence.getInstanceUrl(),
                confluence.getEmail(),
                settingsService.savedConfluenceToken());
    }
}
