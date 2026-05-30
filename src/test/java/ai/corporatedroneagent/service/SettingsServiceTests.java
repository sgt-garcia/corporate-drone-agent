package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SettingsServiceTests {

    @Test
    void normalizesLegacyProviderIds() {
        assertThat(SettingsService.normalizeAiModel("mistral-ai")).isEqualTo("mistral");
        assertThat(SettingsService.normalizeAiModel("google-genai")).isEqualTo("gemini");
        assertThat(SettingsService.normalizeAiModel("google-gemini")).isEqualTo("gemini");
        assertThat(SettingsService.normalizeAiModel("openai-official")).isEqualTo("openai-sdk");
        assertThat(SettingsService.normalizeAiModel("openai-official-sdk")).isEqualTo("openai-sdk");
    }

    @Test
    void keepsCurrentProviderIds() {
        assertThat(SettingsService.normalizeAiModel("mistral")).isEqualTo("mistral");
        assertThat(SettingsService.normalizeAiModel("gemini")).isEqualTo("gemini");
        assertThat(SettingsService.normalizeAiModel("openai-sdk")).isEqualTo("openai-sdk");
        assertThat(SettingsService.normalizeAiModel("none")).isEqualTo("none");
        assertThat(SettingsService.normalizeAiModel("")).isEqualTo("none");
    }
}
