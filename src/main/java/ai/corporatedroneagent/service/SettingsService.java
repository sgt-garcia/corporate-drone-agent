package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.repository.SettingsRepository;
import ai.corporatedroneagent.util.Strings;
import java.util.Objects;
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
        current.setAiModel(normalizeAiModel(settings.getAiModel()));
        current.setCustomInstructions(Strings.emptyIfNull(settings.getCustomInstructions()));
        current.setOpenAi(settings.getOpenAi());
        current.setOpenAiOfficialSdk(settings.getOpenAiOfficialSdk());
        current.setAzureOpenAi(settings.getAzureOpenAi());
        current.setOllama(settings.getOllama());
        current.setMistralAi(settings.getMistralAi());
        current.setGoogleGemini(settings.getGoogleGemini());
        current.setAnthropic(settings.getAnthropic());
        current.setGroq(settings.getGroq());
        current.setDeepSeek(settings.getDeepSeek());
        settingsSecretsService.clearSecretValues(current);
        settingsSecretsService.applySecretStatus(current);
        settingsRepository.save(current);
        eventService.publish("settings-updated", current);
        return current;
    }

    private void migratePlaintextSecrets(ApplicationSettings settings) {
        boolean migrated = settingsSecretsService.migratePlaintextSecrets(settings);
        String normalizedAiModel = normalizeAiModel(settings.getAiModel());
        if (!Objects.equals(settings.getAiModel(), normalizedAiModel)) {
            settings.setAiModel(normalizedAiModel);
            migrated = true;
        }

        if (migrated) {
            settingsSecretsService.applySecretStatus(settings);
            settingsRepository.save(settings);
        }
    }

    static String normalizeAiModel(String aiModel) {
        return switch (Strings.defaultIfBlank(aiModel, "none")) {
            case "openai-official", "openai-official-sdk" -> "openai-sdk";
            case "mistral-ai" -> "mistral";
            case "google-genai", "google-gemini" -> "gemini";
            default -> Strings.defaultIfBlank(aiModel, "none");
        };
    }
}
