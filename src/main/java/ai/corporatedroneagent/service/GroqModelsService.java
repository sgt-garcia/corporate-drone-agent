package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.ApiKeyModelsRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GroqModelsService {

    private final ModelLookupSupport modelLookupSupport;
    private final RestClient restClient;

    public GroqModelsService(ModelLookupSupport modelLookupSupport, RestClient.Builder restClientBuilder) {
        this.modelLookupSupport = modelLookupSupport;
        this.restClient = restClientBuilder
                .baseUrl("https://api.groq.com/openai")
                .build();
    }

    public List<String> listModels(ApiKeyModelsRequest request) {
        return modelLookupSupport.listBearerModels(
                restClient, "Groq models", "/v1/models", apiKeyFor(request),
                GroqModelsService::isChatModelId);
    }

    static boolean isChatModelId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        String normalizedId = id.toLowerCase();
        return !normalizedId.contains("audio")
                && !normalizedId.contains("embedding")
                && !normalizedId.contains("guard")
                && !normalizedId.contains("moderation")
                && !normalizedId.contains("speech")
                && !normalizedId.contains("transcrib")
                && !normalizedId.contains("translate")
                && !normalizedId.contains("tts")
                && !normalizedId.contains("whisper");
    }

    private String apiKeyFor(ApiKeyModelsRequest request) {
        return modelLookupSupport.apiKey(request, settings -> settings.getGroq().getApiKey());
    }
}
