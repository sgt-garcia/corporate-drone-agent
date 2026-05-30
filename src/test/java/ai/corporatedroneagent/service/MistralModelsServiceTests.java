package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MistralModelsServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void identifiesActiveChatModels() throws Exception {
        assertThat(MistralModelsService.isChatModel(objectMapper.readTree(
                """
                        {
                          "id": "mistral-medium-latest",
                          "archived": false,
                          "capabilities": {
                            "completion_chat": true
                          }
                        }
                        """
        ))).isTrue();
    }

    @Test
    void rejectsArchivedOrNonChatModels() throws Exception {
        assertThat(MistralModelsService.isChatModel(objectMapper.readTree(
                """
                        {
                          "id": "mistral-embed",
                          "archived": false,
                          "capabilities": {
                            "completion_chat": false
                          }
                        }
                        """
        ))).isFalse();

        assertThat(MistralModelsService.isChatModel(objectMapper.readTree(
                """
                        {
                          "id": "old-chat-model",
                          "archived": true,
                          "capabilities": {
                            "completion_chat": true
                          }
                        }
                        """
        ))).isFalse();
    }
}
