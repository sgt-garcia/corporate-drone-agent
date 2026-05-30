package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeepSeekModelsServiceTests {

    @Test
    void keepsDeepSeekChatModels() {
        assertThat(DeepSeekModelsService.isChatModelId("deepseek-v4-flash")).isTrue();
        assertThat(DeepSeekModelsService.isChatModelId("deepseek-v4-pro")).isTrue();
        assertThat(DeepSeekModelsService.isChatModelId("deepseek-chat")).isTrue();
        assertThat(DeepSeekModelsService.isChatModelId("deepseek-reasoner")).isTrue();
    }

    @Test
    void excludesNonChatDeepSeekModels() {
        assertThat(DeepSeekModelsService.isChatModelId(null)).isFalse();
        assertThat(DeepSeekModelsService.isChatModelId("")).isFalse();
        assertThat(DeepSeekModelsService.isChatModelId("not-deepseek")).isFalse();
        assertThat(DeepSeekModelsService.isChatModelId("deepseek-embedding")).isFalse();
        assertThat(DeepSeekModelsService.isChatModelId("deepseek-fim")).isFalse();
    }
}
