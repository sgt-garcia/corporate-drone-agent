package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.ApiKeyModelsRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.springframework.http.HttpHeaders;
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
        String apiKey = apiKeyFor(request);
        if (apiKey.isBlank()) {
            return List.of();
        }

        DeepSeekModelsResponse response = modelLookupSupport.request(
                "DeepSeek models",
                () -> restClient.get()
                    .uri("/models")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .body(DeepSeekModelsResponse.class)
        );

        if (response == null || response.data() == null) {
            return List.of();
        }

        return modelLookupSupport.sortedDistinct(response.data().stream()
                .map(DeepSeekModel::id)
                .filter(DeepSeekModelsService::isChatModelId));
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekModelsResponse(List<DeepSeekModel> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekModel(String id) {
    }
}
