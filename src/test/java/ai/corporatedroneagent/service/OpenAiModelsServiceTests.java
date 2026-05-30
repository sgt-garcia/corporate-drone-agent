package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenAiModelsServiceTests {

    @Test
    void identifiesChatModelIds() {
        assertThat(OpenAiModelsService.isChatModelId("gpt-5.2")).isTrue();
        assertThat(OpenAiModelsService.isChatModelId("gpt-4o-mini")).isTrue();
        assertThat(OpenAiModelsService.isChatModelId("o4-mini")).isTrue();
        assertThat(OpenAiModelsService.isChatModelId("chatgpt-4o-latest")).isTrue();
        assertThat(OpenAiModelsService.isChatModelId("ft:gpt-4o-mini:acme:suffix:abc123")).isTrue();
    }

    @Test
    void rejectsNonChatModelIds() {
        assertThat(OpenAiModelsService.isChatModelId("text-embedding-3-large")).isFalse();
        assertThat(OpenAiModelsService.isChatModelId("omni-moderation-latest")).isFalse();
        assertThat(OpenAiModelsService.isChatModelId("gpt-image-1")).isFalse();
        assertThat(OpenAiModelsService.isChatModelId("chatgpt-image-latest")).isFalse();
        assertThat(OpenAiModelsService.isChatModelId("gpt-4o-transcribe")).isFalse();
        assertThat(OpenAiModelsService.isChatModelId("gpt-realtime")).isFalse();
        assertThat(OpenAiModelsService.isChatModelId("gpt-4o-mini-tts")).isFalse();
        assertThat(OpenAiModelsService.isChatModelId("whisper-1")).isFalse();
        assertThat(OpenAiModelsService.isChatModelId("sora-2")).isFalse();
        assertThat(OpenAiModelsService.isChatModelId("dall-e-3")).isFalse();
        assertThat(OpenAiModelsService.isChatModelId("davinci-002")).isFalse();
    }
}
