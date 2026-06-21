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
                          "mistral": {
                            "apiKey": "mistral-secret",
                            "model": "mistral-medium"
                          },
                          "gemini": {
                            "apiKey": "google-secret",
                            "model": "gemini-3.5-flash"
                          },
                          "anthropic": {
                            "apiKey": "anthropic-secret",
                            "model": "claude-sonnet-4-6"
                          },
                          "bedrock": {
                            "region": "eu-central-1",
                            "accessKey": "bedrock-access",
                            "secretKey": "bedrock-secret",
                            "model": "anthropic.claude-sonnet"
                          },
                          "groq": {
                            "apiKey": "groq-secret",
                            "model": "llama-3.3-70b-versatile"
                          },
                          "deepSeek": {
                            "apiKey": "deepseek-secret",
                            "model": "deepseek-v4-pro"
                          }
                        }
                        """,
                ApplicationSettings.class
        );

        assertThat(settings.getOpenAi().getApiKey()).isEqualTo("sk-openai-secret");
        assertThat(settings.getMistral().getApiKey()).isEqualTo("mistral-secret");
        assertThat(settings.getGemini().getApiKey()).isEqualTo("google-secret");
        assertThat(settings.getAnthropic().getApiKey()).isEqualTo("anthropic-secret");
        assertThat(settings.getBedrock().getAccessKey()).isEqualTo("bedrock-access");
        assertThat(settings.getBedrock().getSecretKey()).isEqualTo("bedrock-secret");
        assertThat(settings.getGroq().getApiKey()).isEqualTo("groq-secret");
        assertThat(settings.getDeepSeek().getApiKey()).isEqualTo("deepseek-secret");

        String json = objectMapper.writeValueAsString(settings);

        assertThat(json).doesNotContain("sk-openai-secret");
        assertThat(json).doesNotContain("mistral-secret");
        assertThat(json).doesNotContain("google-secret");
        assertThat(json).doesNotContain("anthropic-secret");
        assertThat(json).doesNotContain("bedrock-access");
        assertThat(json).doesNotContain("bedrock-secret");
        assertThat(json).doesNotContain("groq-secret");
        assertThat(json).doesNotContain("deepseek-secret");
        assertThat(json).doesNotContain("\"apiKey\"");
        assertThat(json).doesNotContain("\"accessKey\"");
        assertThat(json).doesNotContain("\"secretKey\"");
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
