package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.AnthropicModelsRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AnthropicModelsService {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final ModelLookupSupport modelLookupSupport;
    private final RestClient restClient;

    public AnthropicModelsService(ModelLookupSupport modelLookupSupport, RestClient.Builder restClientBuilder) {
        this.modelLookupSupport = modelLookupSupport;
        this.restClient = restClientBuilder
                .baseUrl("https://api.anthropic.com")
                .build();
    }

    public List<String> listModels(AnthropicModelsRequest request) {
        String apiKey = apiKeyFor(request);
        if (apiKey.isBlank()) {
            return List.of();
        }

        JsonNode response = modelLookupSupport.request(
                "Anthropic models",
                () -> restClient.get()
                    .uri("/v1/models?limit=1000")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .retrieve()
                    .body(JsonNode.class)
        );

        return modelLookupSupport.sortedDistinct(modelLookupSupport.elements(response == null ? null : response.path("data"))
                .map(model -> model.path("id").asText(""))
                .filter(AnthropicModelsService::isChatModelId));
    }

    static boolean isChatModelId(String id) {
        return id != null && id.toLowerCase().startsWith("claude-");
    }

    private String apiKeyFor(AnthropicModelsRequest request) {
        return modelLookupSupport.apiKey(
                request == null ? "" : request.getApiKey(),
                request == null || request.isUseSavedKey(),
                settings -> settings.getAnthropic().getApiKey()
        );
    }
}
