package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.OpenAiModelsRequest;
import ai.corporatedroneagent.util.Strings;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OpenAiModelsService {

    private final ModelLookupSupport modelLookupSupport;
    private final RestClient restClient;

    public OpenAiModelsService(ModelLookupSupport modelLookupSupport, RestClient.Builder restClientBuilder) {
        this.modelLookupSupport = modelLookupSupport;
        this.restClient = restClientBuilder
                .baseUrl("https://api.openai.com")
                .build();
    }

    public List<String> listModels(OpenAiModelsRequest request) {
        return modelLookupSupport.listBearerModels(
                restClient, "OpenAI models", "/v1/models", apiKeyFor(request),
                OpenAiModelsService::isChatModelId);
    }

    static boolean isChatModelId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        String normalizedId = id.toLowerCase();
        if (normalizedId.startsWith("ft:")) {
            String[] parts = normalizedId.split(":");
            return parts.length > 1 && isChatModelId(parts[1]);
        }

        if (normalizedId.startsWith("dall-e")
                || normalizedId.startsWith("sora")
                || normalizedId.startsWith("text-embedding")
                || normalizedId.startsWith("text-moderation")
                || normalizedId.startsWith("omni-moderation")
                || normalizedId.startsWith("tts-")
                || normalizedId.startsWith("whisper")
                || normalizedId.startsWith("babbage")
                || normalizedId.startsWith("davinci")
                || normalizedId.startsWith("computer-use")) {
            return false;
        }

        if (normalizedId.contains("audio")
                || normalizedId.contains("embedding")
                || normalizedId.contains("image")
                || normalizedId.contains("moderation")
                || normalizedId.contains("realtime")
                || normalizedId.contains("transcribe")
                || normalizedId.contains("tts")
                || normalizedId.contains("deep-research")) {
            return false;
        }

        return normalizedId.startsWith("gpt-")
                || normalizedId.startsWith("chatgpt-")
                || normalizedId.matches("o\\d.*");
    }

    private String apiKeyFor(OpenAiModelsRequest request) {
        String provider = request == null ? "openai" : Strings.defaultIfBlank(request.getProvider(), "openai");
        return modelLookupSupport.apiKey(request, settings -> switch (provider) {
            case "openai-sdk" -> settings.getOpenAiSdk().getApiKey();
            default -> settings.getOpenAi().getApiKey();
        });
    }
}
