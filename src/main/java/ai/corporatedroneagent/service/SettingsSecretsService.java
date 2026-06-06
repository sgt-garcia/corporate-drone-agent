package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.ApiKeySettings;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.security.SecretStore;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
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
    private static final List<SecretBinding> SECRET_BINDINGS = List.of(
            new SecretBinding(OPENAI_API_KEY, ApplicationSettings::getOpenAi),
            new SecretBinding(OPENAI_SDK_API_KEY, ApplicationSettings::getOpenAiSdk),
            new SecretBinding(AZURE_OPENAI_API_KEY, ApplicationSettings::getAzureOpenAi),
            new SecretBinding(MISTRAL_API_KEY, ApplicationSettings::getMistral),
            new SecretBinding(GEMINI_API_KEY, ApplicationSettings::getGemini),
            new SecretBinding(ANTHROPIC_API_KEY, ApplicationSettings::getAnthropic),
            new SecretBinding(GROQ_API_KEY, ApplicationSettings::getGroq),
            new SecretBinding(DEEPSEEK_API_KEY, ApplicationSettings::getDeepSeek)
    );

    private final SecretStore secretStore;

    public SettingsSecretsService(SecretStore secretStore) {
        this.secretStore = secretStore;
    }

    public boolean migratePlaintextSecrets(ApplicationSettings settings) {
        boolean migrated = false;
        for (SecretBinding binding : SECRET_BINDINGS) {
            migrated = migrateSecret(settings, binding) || migrated;
        }
        return migrated;
    }

    public void saveSubmittedSecrets(ApplicationSettings settings) {
        SECRET_BINDINGS.forEach(binding -> saveSubmittedSecret(settings, binding));
    }

    public void applySecretValues(ApplicationSettings settings) {
        SECRET_BINDINGS.forEach(binding -> {
            String apiKey = secretStore.get(binding.secretKey()).orElse("");
            binding.providerSettings(settings).setApiKey(apiKey);
        });
    }

    public void applySecretStatus(ApplicationSettings settings) {
        SECRET_BINDINGS.forEach(binding -> applyStatus(settings, binding));
    }

    public void clearSecretValues(ApplicationSettings settings) {
        SECRET_BINDINGS.forEach(binding -> {
            ApiKeySettings providerSettings = binding.providerSettings(settings);
            providerSettings.setApiKey("");
            providerSettings.setClearApiKey(false);
        });
    }

    private boolean migrateSecret(ApplicationSettings settings, SecretBinding binding) {
        ApiKeySettings providerSettings = binding.providerSettings(settings);
        String value = providerSettings.getApiKey();
        if (!hasText(value)) {
            return false;
        }
        secretStore.put(binding.secretKey(), value);
        providerSettings.setApiKey("");
        return true;
    }

    private void saveSubmittedSecret(ApplicationSettings settings, SecretBinding binding) {
        ApiKeySettings providerSettings = binding.providerSettings(settings);
        String apiKey = providerSettings.getApiKey();
        if (providerSettings.isClearApiKey()) {
            secretStore.delete(binding.secretKey());
        } else if (hasText(apiKey)) {
            secretStore.put(binding.secretKey(), apiKey);
        }
    }

    private void applyStatus(ApplicationSettings settings, SecretBinding binding) {
        Optional<String> secret = secretStore.get(binding.secretKey());
        ApiKeySettings providerSettings = binding.providerSettings(settings);
        providerSettings.setApiKeyConfigured(secret.isPresent());
        providerSettings.setApiKeyLastFour(secret.map(this::lastFour).orElse(""));
    }

    private String lastFour(String value) {
        return value.substring(Math.max(0, value.length() - 4));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record SecretBinding(
            String secretKey,
            Function<ApplicationSettings, ApiKeySettings> settingsAccessor
    ) {

        ApiKeySettings providerSettings(ApplicationSettings settings) {
            return settingsAccessor.apply(settings);
        }
    }

}
