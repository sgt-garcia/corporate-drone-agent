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
        return modelLookupSupport.listModels(
                restClient, "Mistral models", "/v1/models", apiKey,
                headers -> headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey),
                response -> response.isArray() ? response : response.path("data"),
                model -> isChatModel(model) ? model.path("id").asText("") : null);
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
