package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.security.SecretStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettingsSecretsServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void apiKeysAreAcceptedFromRequestsButNotSerialized() throws Exception {
        ApplicationSettings settings = objectMapper.readValue(
                """
                        {
                          "openAi": {
                            "apiKey": "sk-openai-secret",
                            "model": "gpt-5.5"
                          },
                          "mistralAi": {
                            "apiKey": "mistral-secret",
                            "model": "mistral-medium"
                          },
                          "googleGenAi": {
                            "apiKey": "google-secret",
                            "model": "gemini-2.0-flash"
                          }
                        }
                        """,
                ApplicationSettings.class
        );

        assertThat(settings.getOpenAi().getApiKey()).isEqualTo("sk-openai-secret");
        assertThat(settings.getMistralAi().getApiKey()).isEqualTo("mistral-secret");
        assertThat(settings.getGoogleGenAi().getApiKey()).isEqualTo("google-secret");

        String json = objectMapper.writeValueAsString(settings);

        assertThat(json).doesNotContain("sk-openai-secret");
        assertThat(json).doesNotContain("mistral-secret");
        assertThat(json).doesNotContain("google-secret");
        assertThat(json).doesNotContain("\"apiKey\"");
    }

    @Test
    void migratesPlaintextKeysIntoSecretStoreAndLeavesOnlyStatus() {
        InMemorySecretStore secretStore = new InMemorySecretStore();
        SettingsSecretsService service = new SettingsSecretsService(secretStore);
        ApplicationSettings settings = new ApplicationSettings();
        settings.getOpenAi().setApiKey("sk-openai-secret");
        settings.getMistralAi().setApiKey("mistral-secret");
        settings.getGoogleGenAi().setApiKey("google-secret");

        boolean migrated = service.migratePlaintextSecrets(settings);
        service.applySecretStatus(settings);

        assertThat(migrated).isTrue();
        assertThat(settings.getOpenAi().getApiKey()).isEmpty();
        assertThat(settings.getOpenAi().isApiKeyConfigured()).isTrue();
        assertThat(settings.getOpenAi().getApiKeyLastFour()).isEqualTo("cret");
        assertThat(settings.getMistralAi().getApiKey()).isEmpty();
        assertThat(settings.getMistralAi().isApiKeyConfigured()).isTrue();
        assertThat(settings.getMistralAi().getApiKeyLastFour()).isEqualTo("cret");
        assertThat(settings.getGoogleGenAi().getApiKey()).isEmpty();
        assertThat(settings.getGoogleGenAi().isApiKeyConfigured()).isTrue();
        assertThat(settings.getGoogleGenAi().getApiKeyLastFour()).isEqualTo("cret");
        assertThat(secretStore.get("settings.openAi.apiKey")).contains("sk-openai-secret");
        assertThat(secretStore.get("settings.mistralAi.apiKey")).contains("mistral-secret");
        assertThat(secretStore.get("settings.googleGenAi.apiKey")).contains("google-secret");
    }

    private static class InMemorySecretStore implements SecretStore {

        private final Map<String, String> secrets = new HashMap<>();

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(secrets.get(key));
        }

        @Override
        public void put(String key, String secret) {
            secrets.put(key, secret);
        }

        @Override
        public void delete(String key) {
            secrets.remove(key);
        }
    }
}
