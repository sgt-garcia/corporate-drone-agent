package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AzureOpenAiDeploymentsServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsDeploymentNamesFromDataPlaneAndManagementShapes() throws Exception {
        assertThat(AzureOpenAiDeploymentsService.deploymentName(objectMapper.readTree(
                """
                        {
                          "id": "chat-prod",
                          "model": "gpt-4o"
                        }
                        """
        ))).isEqualTo("chat-prod");

        assertThat(AzureOpenAiDeploymentsService.deploymentName(objectMapper.readTree(
                """
                        {
                          "name": "chat-staging",
                          "properties": {
                            "model": {
                              "name": "gpt-4o-mini"
                            }
                          }
                        }
                        """
        ))).isEqualTo("chat-staging");
    }

    @Test
    void identifiesChatDeploymentsByUnderlyingModelWhenAvailable() throws Exception {
        assertThat(AzureOpenAiDeploymentsService.isChatDeployment(objectMapper.readTree(
                """
                        {
                          "id": "chat-prod",
                          "model": "gpt-4o"
                        }
                        """
        ))).isTrue();

        assertThat(AzureOpenAiDeploymentsService.isChatDeployment(objectMapper.readTree(
                """
                        {
                          "id": "embeddings",
                          "model": "text-embedding-3-large"
                        }
                        """
        ))).isFalse();
    }

    @Test
    void fallsBackToDeploymentNameWhenModelIsUnavailable() throws Exception {
        assertThat(AzureOpenAiDeploymentsService.isChatDeployment(objectMapper.readTree(
                """
                        {
                          "id": "gpt-4o-prod"
                        }
                        """
        ))).isTrue();

        assertThat(AzureOpenAiDeploymentsService.isChatDeployment(objectMapper.readTree(
                """
                        {
                          "id": "text-embedding-3-large"
                        }
                        """
        ))).isFalse();
    }

    @Test
    void trimsTrailingSlashesFromEndpoint() {
        assertThat(AzureOpenAiDeploymentsService.trimTrailingSlashes("https://example.openai.azure.com///"))
                .isEqualTo("https://example.openai.azure.com");
    }
}
