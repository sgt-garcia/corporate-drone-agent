package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.repository.SettingsRepository;
import ai.corporatedroneagent.util.Strings;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {

    private final SettingsRepository settingsRepository;
    private final SettingsSecretsService settingsSecretsService;
    private final EventService eventService;

    public SettingsService(
            SettingsRepository settingsRepository,
            SettingsSecretsService settingsSecretsService,
            EventService eventService
    ) {
        this.settingsRepository = settingsRepository;
        this.settingsSecretsService = settingsSecretsService;
        this.eventService = eventService;
    }

    public ApplicationSettings get() {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        settingsSecretsService.clearSecretValues(settings);
        settingsSecretsService.applySecretStatus(settings);
        return settings;
    }

    public ApplicationSettings getWithSecrets() {
        ApplicationSettings settings = settingsRepository.get();
        migratePlaintextSecrets(settings);
        settingsSecretsService.applySecretValues(settings);
        return settings;
    }

    public ApplicationSettings save(ApplicationSettings settings) {
        ApplicationSettings current = settingsRepository.get();
        migratePlaintextSecrets(current);
        settingsSecretsService.saveSubmittedSecrets(settings);

        current.setAgentName(Strings.defaultIfBlank(settings.getAgentName(), "Corporate Drone Agent"));
        current.setAiModel(Strings.defaultIfBlank(settings.getAiModel(), "none"));
        current.setCustomInstructions(Strings.emptyIfNull(settings.getCustomInstructions()));
        current.setOpenAi(settings.getOpenAi());
        current.setOpenAiOfficial(settings.getOpenAiOfficial());
        current.setAzureOpenAi(settings.getAzureOpenAi());
        current.setOllama(settings.getOllama());
        settingsSecretsService.clearSecretValues(current);
        settingsSecretsService.applySecretStatus(current);
        settingsRepository.save(current);
        eventService.publish("settings-updated", current);
        return current;
    }

    private void migratePlaintextSecrets(ApplicationSettings settings) {
        if (settingsSecretsService.migratePlaintextSecrets(settings)) {
            settingsSecretsService.applySecretStatus(settings);
            settingsRepository.save(settings);
        }
    }
}
