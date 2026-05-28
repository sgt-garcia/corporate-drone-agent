package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.security.SecretStore;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class SettingsSecretsService {

    private static final String OPENAI_API_KEY = "settings.openAi.apiKey";
    private static final String OPENAI_OFFICIAL_API_KEY = "settings.openAiOfficial.apiKey";
    private static final String AZURE_OPENAI_API_KEY = "settings.azureOpenAi.apiKey";

    private final SecretStore secretStore;

    public SettingsSecretsService(SecretStore secretStore) {
        this.secretStore = secretStore;
    }

    public boolean migratePlaintextSecrets(ApplicationSettings settings) {
        boolean migrated = false;

        if (hasText(settings.getOpenAi().getApiKey())) {
            secretStore.put(OPENAI_API_KEY, settings.getOpenAi().getApiKey());
            settings.getOpenAi().setApiKey("");
            migrated = true;
        }

        if (hasText(settings.getOpenAiOfficial().getApiKey())) {
            secretStore.put(OPENAI_OFFICIAL_API_KEY, settings.getOpenAiOfficial().getApiKey());
            settings.getOpenAiOfficial().setApiKey("");
            migrated = true;
        }

        if (hasText(settings.getAzureOpenAi().getApiKey())) {
            secretStore.put(AZURE_OPENAI_API_KEY, settings.getAzureOpenAi().getApiKey());
            settings.getAzureOpenAi().setApiKey("");
            migrated = true;
        }

        return migrated;
    }

    public void saveSubmittedSecrets(ApplicationSettings settings) {
        if (settings.getOpenAi().isClearApiKey()) {
            secretStore.delete(OPENAI_API_KEY);
        } else if (hasText(settings.getOpenAi().getApiKey())) {
            secretStore.put(OPENAI_API_KEY, settings.getOpenAi().getApiKey());
        }

        if (settings.getOpenAiOfficial().isClearApiKey()) {
            secretStore.delete(OPENAI_OFFICIAL_API_KEY);
        } else if (hasText(settings.getOpenAiOfficial().getApiKey())) {
            secretStore.put(OPENAI_OFFICIAL_API_KEY, settings.getOpenAiOfficial().getApiKey());
        }

        if (settings.getAzureOpenAi().isClearApiKey()) {
            secretStore.delete(AZURE_OPENAI_API_KEY);
        } else if (hasText(settings.getAzureOpenAi().getApiKey())) {
            secretStore.put(AZURE_OPENAI_API_KEY, settings.getAzureOpenAi().getApiKey());
        }
    }

    public void applySecretValues(ApplicationSettings settings) {
        settings.getOpenAi().setApiKey(secretStore.get(OPENAI_API_KEY).orElse(""));
        settings.getOpenAiOfficial().setApiKey(secretStore.get(OPENAI_OFFICIAL_API_KEY).orElse(""));
        settings.getAzureOpenAi().setApiKey(secretStore.get(AZURE_OPENAI_API_KEY).orElse(""));
    }

    public void applySecretStatus(ApplicationSettings settings) {
        applyOpenAiStatus(settings, secretStore.get(OPENAI_API_KEY));
        applyOpenAiOfficialStatus(settings, secretStore.get(OPENAI_OFFICIAL_API_KEY));
        applyAzureOpenAiStatus(settings, secretStore.get(AZURE_OPENAI_API_KEY));
    }

    public void clearSecretValues(ApplicationSettings settings) {
        settings.getOpenAi().setApiKey("");
        settings.getOpenAi().setClearApiKey(false);
        settings.getOpenAiOfficial().setApiKey("");
        settings.getOpenAiOfficial().setClearApiKey(false);
        settings.getAzureOpenAi().setApiKey("");
        settings.getAzureOpenAi().setClearApiKey(false);
    }

    private void applyOpenAiStatus(ApplicationSettings settings, Optional<String> secret) {
        settings.getOpenAi().setApiKeyConfigured(secret.isPresent());
        settings.getOpenAi().setApiKeyLastFour(secret.map(this::lastFour).orElse(""));
    }

    private void applyOpenAiOfficialStatus(ApplicationSettings settings, Optional<String> secret) {
        settings.getOpenAiOfficial().setApiKeyConfigured(secret.isPresent());
        settings.getOpenAiOfficial().setApiKeyLastFour(secret.map(this::lastFour).orElse(""));
    }

    private void applyAzureOpenAiStatus(ApplicationSettings settings, Optional<String> secret) {
        settings.getAzureOpenAi().setApiKeyConfigured(secret.isPresent());
        settings.getAzureOpenAi().setApiKeyLastFour(secret.map(this::lastFour).orElse(""));
    }

    private String lastFour(String value) {
        return value.substring(Math.max(0, value.length() - 4));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
