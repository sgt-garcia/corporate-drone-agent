package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.ApiKeySettings;
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
    private static final String BEDROCK_ACCESS_KEY = "settings.bedrock.accessKey";
    private static final String BEDROCK_SECRET_KEY = "settings.bedrock.secretKey";
    private static final String GROQ_API_KEY = "settings.groq.apiKey";
    private static final String DEEPSEEK_API_KEY = "settings.deepSeek.apiKey";
    private static final String JIRA_API_TOKEN = "settings.jira.token";
    private static final String CONFLUENCE_API_TOKEN = "settings.confluence.token";

    // Every persisted secret, in one place. The eight provider API keys share the
    // ApiKeySettings interface (via apiKey(...)); Bedrock's two keys and the Jira/Confluence
    // tokens use their own accessors. Bedrock's secret key has no last-four, so its setter is
    // null and the status pass skips it.
    private static final List<SecretField> SECRETS = List.of(
            apiKey(OPENAI_API_KEY, ApplicationSettings::getOpenAi),
            apiKey(OPENAI_SDK_API_KEY, ApplicationSettings::getOpenAiSdk),
            apiKey(AZURE_OPENAI_API_KEY, ApplicationSettings::getAzureOpenAi),
            apiKey(MISTRAL_API_KEY, ApplicationSettings::getMistral),
            apiKey(GEMINI_API_KEY, ApplicationSettings::getGemini),
            apiKey(ANTHROPIC_API_KEY, ApplicationSettings::getAnthropic),
            apiKey(GROQ_API_KEY, ApplicationSettings::getGroq),
            apiKey(DEEPSEEK_API_KEY, ApplicationSettings::getDeepSeek),
            new SecretField(
                    BEDROCK_ACCESS_KEY,
                    s -> s.getBedrock().getAccessKey(),
                    s -> s.getBedrock().isClearAccessKey(),
                    (s, v) -> s.getBedrock().setAccessKey(v),
                    (s, b) -> s.getBedrock().setClearAccessKey(b),
                    (s, b) -> s.getBedrock().setAccessKeyConfigured(b),
                    (s, v) -> s.getBedrock().setAccessKeyLastFour(v)),
            new SecretField(
                    BEDROCK_SECRET_KEY,
                    s -> s.getBedrock().getSecretKey(),
                    s -> s.getBedrock().isClearSecretKey(),
                    (s, v) -> s.getBedrock().setSecretKey(v),
                    (s, b) -> s.getBedrock().setClearSecretKey(b),
                    (s, b) -> s.getBedrock().setSecretKeyConfigured(b),
                    null),
            new SecretField(
                    JIRA_API_TOKEN,
                    s -> s.getJira().getToken(),
                    s -> s.getJira().isClearToken(),
                    (s, v) -> s.getJira().setToken(v),
                    (s, b) -> s.getJira().setClearToken(b),
                    (s, b) -> s.getJira().setTokenConfigured(b),
                    (s, v) -> s.getJira().setTokenLastFour(v)),
            new SecretField(
                    CONFLUENCE_API_TOKEN,
                    s -> s.getConfluence().getToken(),
                    s -> s.getConfluence().isClearToken(),
                    (s, v) -> s.getConfluence().setToken(v),
                    (s, b) -> s.getConfluence().setClearToken(b),
                    (s, b) -> s.getConfluence().setTokenConfigured(b),
                    (s, v) -> s.getConfluence().setTokenLastFour(v))
    );

    private final SecretStore secretStore;

    public SettingsSecretsService(SecretStore secretStore) {
        this.secretStore = secretStore;
    }

    public void saveSubmittedSecrets(ApplicationSettings settings) {
        SECRETS.forEach(secret -> {
            if (secret.clearRequested().test(settings)) {
                secretStore.delete(secret.secretKey());
                return;
            }
            String value = secret.value().apply(settings);
            if (hasText(value)) {
                secretStore.put(secret.secretKey(), value);
            }
        });
    }

    public void applySecretValues(ApplicationSettings settings) {
        SECRETS.forEach(secret ->
                secret.setValue().accept(settings, secretStore.get(secret.secretKey()).orElse("")));
    }

    // Decrypt a single token rather than the whole settings blob. Each secretStore.get is an
    // unprotect call (a PowerShell/DPAPI fork on Windows), so callers that only need one token
    // should avoid applySecretValues, which decrypts all twelve secrets.
    public String jiraToken() {
        return secretStore.get(JIRA_API_TOKEN).orElse("");
    }

    public String confluenceToken() {
        return secretStore.get(CONFLUENCE_API_TOKEN).orElse("");
    }

    public void applySecretStatus(ApplicationSettings settings) {
        SECRETS.forEach(secret -> {
            Optional<String> stored = secretStore.get(secret.secretKey());
            secret.setConfigured().accept(settings, stored.isPresent());
            if (secret.setLastFour() != null) {
                secret.setLastFour().accept(settings, stored.map(this::lastFour).orElse(""));
            }
        });
    }

    public void clearSecretValues(ApplicationSettings settings) {
        SECRETS.forEach(secret -> {
            secret.setValue().accept(settings, "");
            secret.setClearRequested().accept(settings, false);
        });
    }

    private String lastFour(String value) {
        return value.substring(Math.max(0, value.length() - 4));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static SecretField apiKey(String secretKey, Function<ApplicationSettings, ApiKeySettings> accessor) {
        return new SecretField(
                secretKey,
                s -> accessor.apply(s).getApiKey(),
                s -> accessor.apply(s).isClearApiKey(),
                (s, v) -> accessor.apply(s).setApiKey(v),
                (s, b) -> accessor.apply(s).setClearApiKey(b),
                (s, b) -> accessor.apply(s).setApiKeyConfigured(b),
                (s, v) -> accessor.apply(s).setApiKeyLastFour(v));
    }

    /**
     * One persisted secret: how to read its submitted value and clear flag, and how to write
     * the value, clear flag, configured flag, and (optionally) last-four back onto the settings.
     * {@code setLastFour} is null for secrets that expose no last-four (Bedrock's secret key).
     */
    private record SecretField(
            String secretKey,
            Function<ApplicationSettings, String> value,
            Predicate<ApplicationSettings> clearRequested,
            BiConsumer<ApplicationSettings, String> setValue,
            BiConsumer<ApplicationSettings, Boolean> setClearRequested,
            BiConsumer<ApplicationSettings, Boolean> setConfigured,
            BiConsumer<ApplicationSettings, String> setLastFour
    ) {
    }
}
