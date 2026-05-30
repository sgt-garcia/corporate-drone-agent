package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class GoogleGeminiModelsServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void identifiesGeminiGenerateContentModelsAsChatModels() throws Exception {
        assertThat(GoogleGeminiModelsService.isChatModel(objectMapper.readTree(
                """
                        {
                          "name": "models/gemini-2.5-flash",
                          "supportedGenerationMethods": [
                            "generateContent",
                            "countTokens"
                          ]
                        }
                        """
        ))).isTrue();
    }

    @Test
    void rejectsNonChatOrImageGeminiModels() throws Exception {
        assertThat(GoogleGeminiModelsService.isChatModel(objectMapper.readTree(
                """
                        {
                          "name": "models/text-embedding-004",
                          "supportedGenerationMethods": [
                            "embedContent"
                          ]
                        }
                        """
        ))).isFalse();

        assertThat(GoogleGeminiModelsService.isChatModel(objectMapper.readTree(
                """
                        {
                          "name": "models/gemini-2.0-flash-preview-image-generation",
                          "supportedGenerationMethods": [
                            "generateContent"
                          ]
                        }
                        """
        ))).isFalse();

        assertThat(GoogleGeminiModelsService.isChatModel(objectMapper.readTree(
                """
                        {
                          "name": "models/gemini-live-2.5-flash-preview",
                          "supportedGenerationMethods": [
                            "bidiGenerateContent"
                          ]
                        }
                        """
        ))).isFalse();
    }

    @Test
    void normalizesModelNames() {
        assertThat(GoogleGeminiModelsService.normalizeModelId("models/gemini-2.5-pro"))
                .isEqualTo("gemini-2.5-pro");
        assertThat(GoogleGeminiModelsService.normalizeModelId("gemini-2.5-pro"))
                .isEqualTo("gemini-2.5-pro");
        assertThat(GoogleGeminiModelsService.normalizeModelId(" "))
                .isEmpty();
    }
}
