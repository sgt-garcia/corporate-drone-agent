package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import ai.corporatedroneagent.util.Strings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OllamaModelsServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsModelNameFromTagsResponseItems() throws Exception {
        assertThat(OllamaModelsService.modelName(objectMapper.readTree(
                """
                        {
                          "model": "llama3.2:latest"
                        }
                        """
        ))).isEqualTo("llama3.2:latest");

        assertThat(OllamaModelsService.modelName(objectMapper.readTree(
                """
                        {
                          "name": "gemma3:latest"
                        }
                        """
        ))).isEqualTo("gemma3:latest");
    }

    @Test
    void rejectsEmbeddingModels() {
        assertThat(OllamaModelsService.isChatModelId("llama3.2:latest")).isTrue();
        assertThat(OllamaModelsService.isChatModelId("nomic-embed-text:latest")).isFalse();
        assertThat(OllamaModelsService.isChatModelId("mxbai-embed-large:latest")).isFalse();
    }

    @Test
    void trimsTrailingSlashesFromBaseUrl() {
        assertThat(Strings.trimTrailingSlashes("http://localhost:11434///"))
                .isEqualTo("http://localhost:11434");
    }
}
