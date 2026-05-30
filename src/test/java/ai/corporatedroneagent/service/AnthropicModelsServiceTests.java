package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnthropicModelsServiceTests {

    @Test
    void identifiesClaudeModelIdsAsChatModels() {
        assertThat(AnthropicModelsService.isChatModelId("claude-opus-4-1-20250805")).isTrue();
        assertThat(AnthropicModelsService.isChatModelId("claude-sonnet-4-20250514")).isTrue();
        assertThat(AnthropicModelsService.isChatModelId("claude-3-5-haiku-latest")).isTrue();
    }

    @Test
    void rejectsBlankOrNonClaudeModelIds() {
        assertThat(AnthropicModelsService.isChatModelId("")).isFalse();
        assertThat(AnthropicModelsService.isChatModelId("embedding-model")).isFalse();
        assertThat(AnthropicModelsService.isChatModelId("computer-use")).isFalse();
    }
}
