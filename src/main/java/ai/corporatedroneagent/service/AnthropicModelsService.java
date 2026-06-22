package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.ApiKeyModelsRequest;
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

    public List<String> listModels(ApiKeyModelsRequest request) {
        String apiKey = apiKeyFor(request);
        return modelLookupSupport.listModels(
                restClient, "Anthropic models", "/v1/models?limit=1000", apiKey,
                headers -> {
                    headers.add("x-api-key", apiKey);
                    headers.add("anthropic-version", ANTHROPIC_VERSION);
                },
                response -> response.path("data"),
                model -> {
                    String id = model.path("id").asText("");
                    return isChatModelId(id) ? id : null;
                });
    }

    static boolean isChatModelId(String id) {
        return id != null && id.toLowerCase().startsWith("claude-");
    }

    private String apiKeyFor(ApiKeyModelsRequest request) {
        return modelLookupSupport.apiKey(request, settings -> settings.getAnthropic().getApiKey());
    }
}
