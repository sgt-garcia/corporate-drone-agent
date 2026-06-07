package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrock.model.FoundationModelSummary;
import software.amazon.awssdk.services.bedrock.model.InferenceType;
import software.amazon.awssdk.services.bedrock.model.ModelModality;

class BedrockModelsServiceTests {

    @Test
    void identifiesOnDemandTextModelsAsChatCandidates() {
        FoundationModelSummary model = FoundationModelSummary.builder()
                .modelId("anthropic.claude-sonnet")
                .inputModalities(ModelModality.TEXT)
                .outputModalities(ModelModality.TEXT)
                .inferenceTypesSupported(InferenceType.ON_DEMAND)
                .build();

        assertThat(BedrockModelsService.isChatModel(model)).isTrue();
    }

    @Test
    void rejectsEmbeddingModels() {
        FoundationModelSummary model = FoundationModelSummary.builder()
                .modelId("amazon.titan-embed-text")
                .inputModalities(ModelModality.TEXT)
                .outputModalities(ModelModality.EMBEDDING)
                .inferenceTypesSupported(InferenceType.ON_DEMAND)
                .build();

        assertThat(BedrockModelsService.isChatModel(model)).isFalse();
    }
}
