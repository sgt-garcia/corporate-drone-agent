package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.security.SecretStore;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class SettingsSecretsService {

    private static final String OPENAI_API_KEY = "settings.openAi.apiKey";
    private static final String OPENAI_SDK_API_KEY = "settings.openAiSdk.apiKey";
    private static final String AZURE_OPENAI_API_KEY = "settings.azureOpenAi.apiKey";
    private static final String MISTRAL_API_KEY = "settings.mistral.apiKey";
    private static final String GEMINI_API_KEY = "settings.gemini.apiKey";
    private static final String ANTHROPIC_API_KEY = "settings.anthropic.apiKey";
    private static final String GROQ_API_KEY = "settings.groq.apiKey";
    private static final String DEEPSEEK_API_KEY = "settings.deepSeek.apiKey";

    private final SecretStore secretStore;

    public SettingsSecretsService(SecretStore secretStore) {
        this.secretStore = secretStore;
    }

    public boolean migratePlaintextSecrets(ApplicationSettings settings) {
        boolean migrated = false;
        migrated = migrateSecret(settings.getOpenAi().getApiKey(), OPENAI_API_KEY, settings.getOpenAi()::setApiKey) || migrated;
        migrated = migrateSecret(settings.getOpenAiSdk().getApiKey(), OPENAI_SDK_API_KEY, settings.getOpenAiSdk()::setApiKey) || migrated;
        migrated = migrateSecret(settings.getAzureOpenAi().getApiKey(), AZURE_OPENAI_API_KEY, settings.getAzureOpenAi()::setApiKey) || migrated;
        migrated = migrateSecret(settings.getMistral().getApiKey(), MISTRAL_API_KEY, settings.getMistral()::setApiKey) || migrated;
        migrated = migrateSecret(settings.getGemini().getApiKey(), GEMINI_API_KEY, settings.getGemini()::setApiKey) || migrated;
        migrated = migrateSecret(settings.getAnthropic().getApiKey(), ANTHROPIC_API_KEY, settings.getAnthropic()::setApiKey) || migrated;
        migrated = migrateSecret(settings.getGroq().getApiKey(), GROQ_API_KEY, settings.getGroq()::setApiKey) || migrated;
        migrated = migrateSecret(settings.getDeepSeek().getApiKey(), DEEPSEEK_API_KEY, settings.getDeepSeek()::setApiKey) || migrated;
        return migrated;
    }

    public void saveSubmittedSecrets(ApplicationSettings settings) {
        saveSubmittedSecret(settings.getOpenAi().isClearApiKey(), settings.getOpenAi().getApiKey(), OPENAI_API_KEY);
        saveSubmittedSecret(settings.getOpenAiSdk().isClearApiKey(), settings.getOpenAiSdk().getApiKey(), OPENAI_SDK_API_KEY);
        saveSubmittedSecret(settings.getAzureOpenAi().isClearApiKey(), settings.getAzureOpenAi().getApiKey(), AZURE_OPENAI_API_KEY);
        saveSubmittedSecret(settings.getMistral().isClearApiKey(), settings.getMistral().getApiKey(), MISTRAL_API_KEY);
        saveSubmittedSecret(settings.getGemini().isClearApiKey(), settings.getGemini().getApiKey(), GEMINI_API_KEY);
        saveSubmittedSecret(settings.getAnthropic().isClearApiKey(), settings.getAnthropic().getApiKey(), ANTHROPIC_API_KEY);
        saveSubmittedSecret(settings.getGroq().isClearApiKey(), settings.getGroq().getApiKey(), GROQ_API_KEY);
        saveSubmittedSecret(settings.getDeepSeek().isClearApiKey(), settings.getDeepSeek().getApiKey(), DEEPSEEK_API_KEY);
    }

    public void applySecretValues(ApplicationSettings settings) {
        settings.getOpenAi().setApiKey(secretStore.get(OPENAI_API_KEY).orElse(""));
        settings.getOpenAiSdk().setApiKey(secretStore.get(OPENAI_SDK_API_KEY).orElse(""));
        settings.getAzureOpenAi().setApiKey(secretStore.get(AZURE_OPENAI_API_KEY).orElse(""));
        settings.getMistral().setApiKey(secretStore.get(MISTRAL_API_KEY).orElse(""));
        settings.getGemini().setApiKey(secretStore.get(GEMINI_API_KEY).orElse(""));
        settings.getAnthropic().setApiKey(secretStore.get(ANTHROPIC_API_KEY).orElse(""));
        settings.getGroq().setApiKey(secretStore.get(GROQ_API_KEY).orElse(""));
        settings.getDeepSeek().setApiKey(secretStore.get(DEEPSEEK_API_KEY).orElse(""));
    }

    public void applySecretStatus(ApplicationSettings settings) {
        applyStatus(secretStore.get(OPENAI_API_KEY), settings.getOpenAi()::setApiKeyConfigured, settings.getOpenAi()::setApiKeyLastFour);
        applyStatus(secretStore.get(OPENAI_SDK_API_KEY), settings.getOpenAiSdk()::setApiKeyConfigured, settings.getOpenAiSdk()::setApiKeyLastFour);
        applyStatus(secretStore.get(AZURE_OPENAI_API_KEY), settings.getAzureOpenAi()::setApiKeyConfigured, settings.getAzureOpenAi()::setApiKeyLastFour);
        applyStatus(secretStore.get(MISTRAL_API_KEY), settings.getMistral()::setApiKeyConfigured, settings.getMistral()::setApiKeyLastFour);
        applyStatus(secretStore.get(GEMINI_API_KEY), settings.getGemini()::setApiKeyConfigured, settings.getGemini()::setApiKeyLastFour);
        applyStatus(secretStore.get(ANTHROPIC_API_KEY), settings.getAnthropic()::setApiKeyConfigured, settings.getAnthropic()::setApiKeyLastFour);
        applyStatus(secretStore.get(GROQ_API_KEY), settings.getGroq()::setApiKeyConfigured, settings.getGroq()::setApiKeyLastFour);
        applyStatus(secretStore.get(DEEPSEEK_API_KEY), settings.getDeepSeek()::setApiKeyConfigured, settings.getDeepSeek()::setApiKeyLastFour);
    }

    public void clearSecretValues(ApplicationSettings settings) {
        settings.getOpenAi().setApiKey("");
        settings.getOpenAi().setClearApiKey(false);
        settings.getOpenAiSdk().setApiKey("");
        settings.getOpenAiSdk().setClearApiKey(false);
        settings.getAzureOpenAi().setApiKey("");
        settings.getAzureOpenAi().setClearApiKey(false);
        settings.getMistral().setApiKey("");
        settings.getMistral().setClearApiKey(false);
        settings.getGemini().setApiKey("");
        settings.getGemini().setClearApiKey(false);
        settings.getAnthropic().setApiKey("");
        settings.getAnthropic().setClearApiKey(false);
        settings.getGroq().setApiKey("");
        settings.getGroq().setClearApiKey(false);
        settings.getDeepSeek().setApiKey("");
        settings.getDeepSeek().setClearApiKey(false);
    }

    private boolean migrateSecret(String value, String secretKey, Consumer<String> clearPlaintext) {
        if (!hasText(value)) {
            return false;
        }
        secretStore.put(secretKey, value);
        clearPlaintext.accept("");
        return true;
    }

    private void saveSubmittedSecret(boolean clear, String value, String secretKey) {
        if (clear) {
            secretStore.delete(secretKey);
        } else if (hasText(value)) {
            secretStore.put(secretKey, value);
        }
    }

    private void applyStatus(
            Optional<String> secret,
            Consumer<Boolean> configuredSetter,
            Consumer<String> lastFourSetter
    ) {
        configuredSetter.accept(secret.isPresent());
        lastFourSetter.accept(secret.map(this::lastFour).orElse(""));
    }

    private String lastFour(String value) {
        return value.substring(Math.max(0, value.length() - 4));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
