package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.repository.SettingsRepository;
import ai.corporatedroneagent.util.Strings;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {

    private final SettingsRepository settingsRepository;
    private final EventService eventService;

    public SettingsService(SettingsRepository settingsRepository, EventService eventService) {
        this.settingsRepository = settingsRepository;
        this.eventService = eventService;
    }

    public ApplicationSettings get() {
        return settingsRepository.get();
    }

    public ApplicationSettings save(ApplicationSettings settings) {
        ApplicationSettings current = settingsRepository.get();
        current.setAgentName(Strings.defaultIfBlank(settings.getAgentName(), "Corporate Drone Agent"));
        current.setAiModel(Strings.defaultIfBlank(settings.getAiModel(), "none"));
        current.setCustomInstructions(Strings.emptyIfNull(settings.getCustomInstructions()));
        current.setOpenAi(settings.getOpenAi());
        current.setOpenAiOfficial(settings.getOpenAiOfficial());
        current.setAzureOpenAi(settings.getAzureOpenAi());
        settingsRepository.save(current);
        eventService.publish("settings-updated", current);
        return current;
    }
}
