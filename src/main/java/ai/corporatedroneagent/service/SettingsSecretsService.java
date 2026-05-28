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
    private static final String MISTRAL_AI_API_KEY = "settings.mistralAi.apiKey";
    private static final String GOOGLE_GENAI_API_KEY = "settings.googleGenAi.apiKey";
    private static final String ANTHROPIC_API_KEY = "settings.anthropic.apiKey";

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

        if (hasText(settings.getMistralAi().getApiKey())) {
            secretStore.put(MISTRAL_AI_API_KEY, settings.getMistralAi().getApiKey());
            settings.getMistralAi().setApiKey("");
            migrated = true;
        }

        if (hasText(settings.getGoogleGenAi().getApiKey())) {
            secretStore.put(GOOGLE_GENAI_API_KEY, settings.getGoogleGenAi().getApiKey());
            settings.getGoogleGenAi().setApiKey("");
            migrated = true;
        }

        if (hasText(settings.getAnthropic().getApiKey())) {
            secretStore.put(ANTHROPIC_API_KEY, settings.getAnthropic().getApiKey());
            settings.getAnthropic().setApiKey("");
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

        if (settings.getMistralAi().isClearApiKey()) {
            secretStore.delete(MISTRAL_AI_API_KEY);
        } else if (hasText(settings.getMistralAi().getApiKey())) {
            secretStore.put(MISTRAL_AI_API_KEY, settings.getMistralAi().getApiKey());
        }

        if (settings.getGoogleGenAi().isClearApiKey()) {
            secretStore.delete(GOOGLE_GENAI_API_KEY);
        } else if (hasText(settings.getGoogleGenAi().getApiKey())) {
            secretStore.put(GOOGLE_GENAI_API_KEY, settings.getGoogleGenAi().getApiKey());
        }

        if (settings.getAnthropic().isClearApiKey()) {
            secretStore.delete(ANTHROPIC_API_KEY);
        } else if (hasText(settings.getAnthropic().getApiKey())) {
            secretStore.put(ANTHROPIC_API_KEY, settings.getAnthropic().getApiKey());
        }
    }

    public void applySecretValues(ApplicationSettings settings) {
        settings.getOpenAi().setApiKey(secretStore.get(OPENAI_API_KEY).orElse(""));
        settings.getOpenAiOfficial().setApiKey(secretStore.get(OPENAI_OFFICIAL_API_KEY).orElse(""));
        settings.getAzureOpenAi().setApiKey(secretStore.get(AZURE_OPENAI_API_KEY).orElse(""));
        settings.getMistralAi().setApiKey(secretStore.get(MISTRAL_AI_API_KEY).orElse(""));
        settings.getGoogleGenAi().setApiKey(secretStore.get(GOOGLE_GENAI_API_KEY).orElse(""));
        settings.getAnthropic().setApiKey(secretStore.get(ANTHROPIC_API_KEY).orElse(""));
    }

    public void applySecretStatus(ApplicationSettings settings) {
        applyOpenAiStatus(settings, secretStore.get(OPENAI_API_KEY));
        applyOpenAiOfficialStatus(settings, secretStore.get(OPENAI_OFFICIAL_API_KEY));
        applyAzureOpenAiStatus(settings, secretStore.get(AZURE_OPENAI_API_KEY));
        applyMistralAiStatus(settings, secretStore.get(MISTRAL_AI_API_KEY));
        applyGoogleGenAiStatus(settings, secretStore.get(GOOGLE_GENAI_API_KEY));
        applyAnthropicStatus(settings, secretStore.get(ANTHROPIC_API_KEY));
    }

    public void clearSecretValues(ApplicationSettings settings) {
        settings.getOpenAi().setApiKey("");
        settings.getOpenAi().setClearApiKey(false);
        settings.getOpenAiOfficial().setApiKey("");
        settings.getOpenAiOfficial().setClearApiKey(false);
        settings.getAzureOpenAi().setApiKey("");
        settings.getAzureOpenAi().setClearApiKey(false);
        settings.getMistralAi().setApiKey("");
        settings.getMistralAi().setClearApiKey(false);
        settings.getGoogleGenAi().setApiKey("");
        settings.getGoogleGenAi().setClearApiKey(false);
        settings.getAnthropic().setApiKey("");
        settings.getAnthropic().setClearApiKey(false);
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

    private void applyMistralAiStatus(ApplicationSettings settings, Optional<String> secret) {
        settings.getMistralAi().setApiKeyConfigured(secret.isPresent());
        settings.getMistralAi().setApiKeyLastFour(secret.map(this::lastFour).orElse(""));
    }

    private void applyGoogleGenAiStatus(ApplicationSettings settings, Optional<String> secret) {
        settings.getGoogleGenAi().setApiKeyConfigured(secret.isPresent());
        settings.getGoogleGenAi().setApiKeyLastFour(secret.map(this::lastFour).orElse(""));
    }

    private void applyAnthropicStatus(ApplicationSettings settings, Optional<String> secret) {
        settings.getAnthropic().setApiKeyConfigured(secret.isPresent());
        settings.getAnthropic().setApiKeyLastFour(secret.map(this::lastFour).orElse(""));
    }

    private String lastFour(String value) {
        return value.substring(Math.max(0, value.length() - 4));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
