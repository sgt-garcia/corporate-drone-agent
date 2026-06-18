package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.ApiKeySettings;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.model.BedrockSettings;
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
    private static final String BEDROCK_ACCESS_KEY = "settings.bedrock.accessKey";
    private static final String BEDROCK_SECRET_KEY = "settings.bedrock.secretKey";
    private static final String GROQ_API_KEY = "settings.groq.apiKey";
    private static final String DEEPSEEK_API_KEY = "settings.deepSeek.apiKey";
    private static final String JIRA_API_TOKEN = "settings.jira.token";
    private static final String CONFLUENCE_API_TOKEN = "settings.confluence.token";
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
        migrated = migrateBedrockSecrets(settings) || migrated;
        migrated = migrateJiraToken(settings) || migrated;
        migrated = migrateConfluenceToken(settings) || migrated;
        return migrated;
    }

    public void saveSubmittedSecrets(ApplicationSettings settings) {
        SECRET_BINDINGS.forEach(binding -> saveSubmittedSecret(settings, binding));
        saveSubmittedBedrockSecrets(settings);
        saveSubmittedJiraToken(settings);
        saveSubmittedConfluenceToken(settings);
    }

    public void applySecretValues(ApplicationSettings settings) {
        SECRET_BINDINGS.forEach(binding -> {
            String apiKey = secretStore.get(binding.secretKey()).orElse("");
            binding.providerSettings(settings).setApiKey(apiKey);
        });
        applyBedrockSecretValues(settings);
        settings.getJira().setToken(secretStore.get(JIRA_API_TOKEN).orElse(""));
        settings.getConfluence().setToken(secretStore.get(CONFLUENCE_API_TOKEN).orElse(""));
    }

    public void applySecretStatus(ApplicationSettings settings) {
        SECRET_BINDINGS.forEach(binding -> applyStatus(settings, binding));
        applyBedrockSecretStatus(settings);
        applyJiraTokenStatus(settings);
        applyConfluenceTokenStatus(settings);
    }

    public void clearSecretValues(ApplicationSettings settings) {
        SECRET_BINDINGS.forEach(binding -> {
            ApiKeySettings providerSettings = binding.providerSettings(settings);
            providerSettings.setApiKey("");
            providerSettings.setClearApiKey(false);
        });
        clearBedrockSecretValues(settings);
        settings.getJira().setToken("");
        settings.getJira().setClearToken(false);
        settings.getConfluence().setToken("");
        settings.getConfluence().setClearToken(false);
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

    private boolean migrateBedrockSecrets(ApplicationSettings settings) {
        BedrockSettings bedrock = settings.getBedrock();
        boolean migrated = false;
        if (hasText(bedrock.getAccessKey())) {
            secretStore.put(BEDROCK_ACCESS_KEY, bedrock.getAccessKey());
            bedrock.setAccessKey("");
            migrated = true;
        }
        if (hasText(bedrock.getSecretKey())) {
            secretStore.put(BEDROCK_SECRET_KEY, bedrock.getSecretKey());
            bedrock.setSecretKey("");
            migrated = true;
        }
        return migrated;
    }

    private void saveSubmittedBedrockSecrets(ApplicationSettings settings) {
        BedrockSettings bedrock = settings.getBedrock();
        if (bedrock.isClearAccessKey()) {
            secretStore.delete(BEDROCK_ACCESS_KEY);
        } else if (hasText(bedrock.getAccessKey())) {
            secretStore.put(BEDROCK_ACCESS_KEY, bedrock.getAccessKey());
        }

        if (bedrock.isClearSecretKey()) {
            secretStore.delete(BEDROCK_SECRET_KEY);
        } else if (hasText(bedrock.getSecretKey())) {
            secretStore.put(BEDROCK_SECRET_KEY, bedrock.getSecretKey());
        }
    }

    private void applyBedrockSecretValues(ApplicationSettings settings) {
        BedrockSettings bedrock = settings.getBedrock();
        bedrock.setAccessKey(secretStore.get(BEDROCK_ACCESS_KEY).orElse(""));
        bedrock.setSecretKey(secretStore.get(BEDROCK_SECRET_KEY).orElse(""));
    }

    private void applyBedrockSecretStatus(ApplicationSettings settings) {
        BedrockSettings bedrock = settings.getBedrock();
        Optional<String> accessKey = secretStore.get(BEDROCK_ACCESS_KEY);
        Optional<String> secretKey = secretStore.get(BEDROCK_SECRET_KEY);
        bedrock.setAccessKeyConfigured(accessKey.isPresent());
        bedrock.setAccessKeyLastFour(accessKey.map(this::lastFour).orElse(""));
        bedrock.setSecretKeyConfigured(secretKey.isPresent());
    }

    private void clearBedrockSecretValues(ApplicationSettings settings) {
        BedrockSettings bedrock = settings.getBedrock();
        bedrock.setAccessKey("");
        bedrock.setClearAccessKey(false);
        bedrock.setSecretKey("");
        bedrock.setClearSecretKey(false);
    }

    private boolean migrateJiraToken(ApplicationSettings settings) {
        String token = settings.getJira().getToken();
        if (!hasText(token)) {
            return false;
        }
        secretStore.put(JIRA_API_TOKEN, token);
        settings.getJira().setToken("");
        return true;
    }

    private void saveSubmittedJiraToken(ApplicationSettings settings) {
        var jira = settings.getJira();
        if (jira.isClearToken()) {
            secretStore.delete(JIRA_API_TOKEN);
        } else if (hasText(jira.getToken())) {
            secretStore.put(JIRA_API_TOKEN, jira.getToken());
        }
    }

    private void applyJiraTokenStatus(ApplicationSettings settings) {
        Optional<String> token = secretStore.get(JIRA_API_TOKEN);
        var jira = settings.getJira();
        jira.setTokenConfigured(token.isPresent());
        jira.setTokenLastFour(token.map(this::lastFour).orElse(""));
    }

    private boolean migrateConfluenceToken(ApplicationSettings settings) {
        String token = settings.getConfluence().getToken();
        if (!hasText(token)) {
            return false;
        }
        secretStore.put(CONFLUENCE_API_TOKEN, token);
        settings.getConfluence().setToken("");
        return true;
    }

    private void saveSubmittedConfluenceToken(ApplicationSettings settings) {
        var confluence = settings.getConfluence();
        if (confluence.isClearToken()) {
            secretStore.delete(CONFLUENCE_API_TOKEN);
        } else if (hasText(confluence.getToken())) {
            secretStore.put(CONFLUENCE_API_TOKEN, confluence.getToken());
        }
    }

    private void applyConfluenceTokenStatus(ApplicationSettings settings) {
        Optional<String> token = secretStore.get(CONFLUENCE_API_TOKEN);
        var confluence = settings.getConfluence();
        confluence.setTokenConfigured(token.isPresent());
        confluence.setTokenLastFour(token.map(this::lastFour).orElse(""));
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
