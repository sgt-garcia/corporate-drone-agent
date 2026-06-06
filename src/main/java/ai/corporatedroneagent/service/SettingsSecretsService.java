package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.security.SecretStore;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
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
            new SecretBinding(
                    OPENAI_API_KEY,
                    settings -> settings.getOpenAi().getApiKey(),
                    (settings, value) -> settings.getOpenAi().setApiKey(value),
                    settings -> settings.getOpenAi().isClearApiKey(),
                    (settings, clear) -> settings.getOpenAi().setClearApiKey(clear),
                    (settings, configured) -> settings.getOpenAi().setApiKeyConfigured(configured),
                    (settings, lastFour) -> settings.getOpenAi().setApiKeyLastFour(lastFour)
            ),
            new SecretBinding(
                    OPENAI_SDK_API_KEY,
                    settings -> settings.getOpenAiSdk().getApiKey(),
                    (settings, value) -> settings.getOpenAiSdk().setApiKey(value),
                    settings -> settings.getOpenAiSdk().isClearApiKey(),
                    (settings, clear) -> settings.getOpenAiSdk().setClearApiKey(clear),
                    (settings, configured) -> settings.getOpenAiSdk().setApiKeyConfigured(configured),
                    (settings, lastFour) -> settings.getOpenAiSdk().setApiKeyLastFour(lastFour)
            ),
            new SecretBinding(
                    AZURE_OPENAI_API_KEY,
                    settings -> settings.getAzureOpenAi().getApiKey(),
                    (settings, value) -> settings.getAzureOpenAi().setApiKey(value),
                    settings -> settings.getAzureOpenAi().isClearApiKey(),
                    (settings, clear) -> settings.getAzureOpenAi().setClearApiKey(clear),
                    (settings, configured) -> settings.getAzureOpenAi().setApiKeyConfigured(configured),
                    (settings, lastFour) -> settings.getAzureOpenAi().setApiKeyLastFour(lastFour)
            ),
            new SecretBinding(
                    MISTRAL_API_KEY,
                    settings -> settings.getMistral().getApiKey(),
                    (settings, value) -> settings.getMistral().setApiKey(value),
                    settings -> settings.getMistral().isClearApiKey(),
                    (settings, clear) -> settings.getMistral().setClearApiKey(clear),
                    (settings, configured) -> settings.getMistral().setApiKeyConfigured(configured),
                    (settings, lastFour) -> settings.getMistral().setApiKeyLastFour(lastFour)
            ),
            new SecretBinding(
                    GEMINI_API_KEY,
                    settings -> settings.getGemini().getApiKey(),
                    (settings, value) -> settings.getGemini().setApiKey(value),
                    settings -> settings.getGemini().isClearApiKey(),
                    (settings, clear) -> settings.getGemini().setClearApiKey(clear),
                    (settings, configured) -> settings.getGemini().setApiKeyConfigured(configured),
                    (settings, lastFour) -> settings.getGemini().setApiKeyLastFour(lastFour)
            ),
            new SecretBinding(
                    ANTHROPIC_API_KEY,
                    settings -> settings.getAnthropic().getApiKey(),
                    (settings, value) -> settings.getAnthropic().setApiKey(value),
                    settings -> settings.getAnthropic().isClearApiKey(),
                    (settings, clear) -> settings.getAnthropic().setClearApiKey(clear),
                    (settings, configured) -> settings.getAnthropic().setApiKeyConfigured(configured),
                    (settings, lastFour) -> settings.getAnthropic().setApiKeyLastFour(lastFour)
            ),
            new SecretBinding(
                    GROQ_API_KEY,
                    settings -> settings.getGroq().getApiKey(),
                    (settings, value) -> settings.getGroq().setApiKey(value),
                    settings -> settings.getGroq().isClearApiKey(),
                    (settings, clear) -> settings.getGroq().setClearApiKey(clear),
                    (settings, configured) -> settings.getGroq().setApiKeyConfigured(configured),
                    (settings, lastFour) -> settings.getGroq().setApiKeyLastFour(lastFour)
            ),
            new SecretBinding(
                    DEEPSEEK_API_KEY,
                    settings -> settings.getDeepSeek().getApiKey(),
                    (settings, value) -> settings.getDeepSeek().setApiKey(value),
                    settings -> settings.getDeepSeek().isClearApiKey(),
                    (settings, clear) -> settings.getDeepSeek().setClearApiKey(clear),
                    (settings, configured) -> settings.getDeepSeek().setApiKeyConfigured(configured),
                    (settings, lastFour) -> settings.getDeepSeek().setApiKeyLastFour(lastFour)
            )
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
            binding.setApiKey(settings, apiKey);
        });
    }

    public void applySecretStatus(ApplicationSettings settings) {
        SECRET_BINDINGS.forEach(binding -> applyStatus(settings, binding));
    }

    public void clearSecretValues(ApplicationSettings settings) {
        SECRET_BINDINGS.forEach(binding -> {
            binding.setApiKey(settings, "");
            binding.setClearApiKey(settings, false);
        });
    }

    private boolean migrateSecret(ApplicationSettings settings, SecretBinding binding) {
        String value = binding.apiKey(settings);
        if (!hasText(value)) {
            return false;
        }
        secretStore.put(binding.secretKey(), value);
        binding.setApiKey(settings, "");
        return true;
    }

    private void saveSubmittedSecret(ApplicationSettings settings, SecretBinding binding) {
        String apiKey = binding.apiKey(settings);
        if (binding.clearApiKey(settings)) {
            secretStore.delete(binding.secretKey());
        } else if (hasText(apiKey)) {
            secretStore.put(binding.secretKey(), apiKey);
        }
    }

    private void applyStatus(ApplicationSettings settings, SecretBinding binding) {
        Optional<String> secret = secretStore.get(binding.secretKey());
        binding.setApiKeyConfigured(settings, secret.isPresent());
        binding.setApiKeyLastFour(settings, secret.map(this::lastFour).orElse(""));
    }

    private String lastFour(String value) {
        return value.substring(Math.max(0, value.length() - 4));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record SecretBinding(
            String secretKey,
            Function<ApplicationSettings, String> apiKeyGetter,
            BiConsumer<ApplicationSettings, String> apiKeySetter,
            Predicate<ApplicationSettings> clearApiKeyGetter,
            BiConsumer<ApplicationSettings, Boolean> clearApiKeySetter,
            BiConsumer<ApplicationSettings, Boolean> apiKeyConfiguredSetter,
            BiConsumer<ApplicationSettings, String> apiKeyLastFourSetter
    ) {

        String apiKey(ApplicationSettings settings) {
            return apiKeyGetter.apply(settings);
        }

        void setApiKey(ApplicationSettings settings, String apiKey) {
            apiKeySetter.accept(settings, apiKey);
        }

        boolean clearApiKey(ApplicationSettings settings) {
            return clearApiKeyGetter.test(settings);
        }

        void setClearApiKey(ApplicationSettings settings, boolean clearApiKey) {
            clearApiKeySetter.accept(settings, clearApiKey);
        }

        void setApiKeyConfigured(ApplicationSettings settings, boolean configured) {
            apiKeyConfiguredSetter.accept(settings, configured);
        }

        void setApiKeyLastFour(ApplicationSettings settings, String lastFour) {
            apiKeyLastFourSetter.accept(settings, lastFour);
        }
    }

}
