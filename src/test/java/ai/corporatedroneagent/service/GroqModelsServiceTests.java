package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GroqModelsServiceTests {

    @Test
    void keepsGroqChatModels() {
        assertThat(GroqModelsService.isChatModelId("llama-3.3-70b-versatile")).isTrue();
        assertThat(GroqModelsService.isChatModelId("openai/gpt-oss-120b")).isTrue();
        assertThat(GroqModelsService.isChatModelId("mixtral-8x7b-32768")).isTrue();
    }

    @Test
    void excludesNonChatGroqModels() {
        assertThat(GroqModelsService.isChatModelId(null)).isFalse();
        assertThat(GroqModelsService.isChatModelId("")).isFalse();
        assertThat(GroqModelsService.isChatModelId("whisper-large-v3")).isFalse();
        assertThat(GroqModelsService.isChatModelId("playai-tts")).isFalse();
        assertThat(GroqModelsService.isChatModelId("llama-guard-4-12b")).isFalse();
    }
}
