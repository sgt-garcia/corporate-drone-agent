package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.ApiKeyModelsRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GeminiModelsService {

    private final ModelLookupSupport modelLookupSupport;
    private final RestClient restClient;

    public GeminiModelsService(ModelLookupSupport modelLookupSupport, RestClient.Builder restClientBuilder) {
        this.modelLookupSupport = modelLookupSupport;
        this.restClient = restClientBuilder
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
    }

    public List<String> listModels(ApiKeyModelsRequest request) {
        String apiKey = apiKeyFor(request);
        return modelLookupSupport.listModels(
                restClient, "Gemini models", "/v1beta/models", apiKey,
                headers -> headers.add("x-goog-api-key", apiKey),
                response -> response.path("models"),
                model -> isChatModel(model) ? normalizeModelId(model.path("name").asText("")) : null);
    }

    static boolean isChatModel(JsonNode model) {
        if (model == null) {
            return false;
        }

        String id = normalizeModelId(model.path("name").asText(""));
        String normalizedId = id.toLowerCase();
        return normalizedId.startsWith("gemini-")
                && !normalizedId.contains("embedding")
                && !normalizedId.contains("imagen")
                && !normalizedId.contains("image")
                && StreamSupport.stream(model.path("supportedGenerationMethods").spliterator(), false)
                .map(JsonNode::asText)
                .anyMatch("generateContent"::equals);
    }

    static String normalizeModelId(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String trimmed = name.trim();
        return trimmed.startsWith("models/") ? trimmed.substring("models/".length()) : trimmed;
    }

    private String apiKeyFor(ApiKeyModelsRequest request) {
        return modelLookupSupport.apiKey(request, settings -> settings.getGemini().getApiKey());
    }
}
