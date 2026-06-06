package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.OpenAiModelsRequest;
import ai.corporatedroneagent.util.Strings;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import org.springframework.http.HttpHeaders;
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
        String apiKey = apiKeyFor(request);
        if (apiKey.isBlank()) {
            return List.of();
        }

        OpenAiModelsResponse response = modelLookupSupport.request(
                "OpenAI models",
                () -> restClient.get()
                    .uri("/v1/models")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .body(OpenAiModelsResponse.class)
        );

        if (response == null || response.data() == null) {
            return List.of();
        }

        return modelLookupSupport.sortedDistinct(response.data().stream()
                .map(OpenAiModel::id)
                .filter(OpenAiModelsService::isChatModelId));
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
        return modelLookupSupport.apiKey(
                request == null ? "" : request.getApiKey(),
                request == null || request.isUseSavedKey(),
                settings -> switch (provider) {
                    case "openai-sdk" -> settings.getOpenAiSdk().getApiKey();
                    default -> settings.getOpenAi().getApiKey();
                }
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiModelsResponse(List<OpenAiModel> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiModel(String id) {
    }
}
