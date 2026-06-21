package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.ApiKeyModelsRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class MistralModelsService {

    private final ModelLookupSupport modelLookupSupport;
    private final RestClient restClient;

    public MistralModelsService(ModelLookupSupport modelLookupSupport, RestClient.Builder restClientBuilder) {
        this.modelLookupSupport = modelLookupSupport;
        this.restClient = restClientBuilder
                .baseUrl("https://api.mistral.ai")
                .build();
    }

    public List<String> listModels(ApiKeyModelsRequest request) {
        String apiKey = apiKeyFor(request);
        if (apiKey.isBlank()) {
            return List.of();
        }

        JsonNode response = modelLookupSupport.request(
                "Mistral models",
                () -> restClient.get()
                    .uri("/v1/models")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .body(JsonNode.class)
        );

        if (response == null) {
            return List.of();
        }

        JsonNode data = response.isArray() ? response : response.path("data");
        return modelLookupSupport.sortedDistinct(modelLookupSupport.elements(data)
                .filter(MistralModelsService::isChatModel)
                .map(model -> model.path("id").asText("")));
    }

    static boolean isChatModel(JsonNode model) {
        return model != null
                && model.path("capabilities").path("completion_chat").asBoolean(false)
                && !model.path("archived").asBoolean(false);
    }

    private String apiKeyFor(ApiKeyModelsRequest request) {
        return modelLookupSupport.apiKey(request, settings -> settings.getMistral().getApiKey());
    }
}
