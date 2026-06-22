package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.ApiKeyModelsRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class DeepSeekModelsService {

    private final ModelLookupSupport modelLookupSupport;
    private final RestClient restClient;

    public DeepSeekModelsService(ModelLookupSupport modelLookupSupport, RestClient.Builder restClientBuilder) {
        this.modelLookupSupport = modelLookupSupport;
        this.restClient = restClientBuilder
                .baseUrl("https://api.deepseek.com")
                .build();
    }

    public List<String> listModels(ApiKeyModelsRequest request) {
        return modelLookupSupport.listBearerModels(
                restClient, "DeepSeek models", "/models", apiKeyFor(request),
                DeepSeekModelsService::isChatModelId);
    }

    static boolean isChatModelId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        String normalizedId = id.toLowerCase();
        return normalizedId.startsWith("deepseek-")
                && !normalizedId.contains("embedding")
                && !normalizedId.contains("fim");
    }

    private String apiKeyFor(ApiKeyModelsRequest request) {
        return modelLookupSupport.apiKey(request, settings -> settings.getDeepSeek().getApiKey());
    }
}
