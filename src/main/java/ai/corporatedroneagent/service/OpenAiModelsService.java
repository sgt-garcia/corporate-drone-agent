package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.OpenAiModelsRequest;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.util.Strings;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OpenAiModelsService {

    private final SettingsService settingsService;
    private final RestClient restClient;

    public OpenAiModelsService(SettingsService settingsService, RestClient.Builder restClientBuilder) {
        this.settingsService = settingsService;
        this.restClient = restClientBuilder
                .baseUrl("https://api.openai.com")
                .build();
    }

    public List<String> listModels(OpenAiModelsRequest request) {
        String apiKey = apiKeyFor(request);
        if (apiKey.isBlank()) {
            return List.of();
        }

        OpenAiModelsResponse response;
        try {
            response = restClient.get()
                    .uri("/v1/models")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .body(OpenAiModelsResponse.class);
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "OpenAI models request failed: "
                            + exception.getStatusCode().value()
                            + " "
                            + exception.getStatusText()
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI models request failed.");
        }

        if (response == null || response.data() == null) {
            return List.of();
        }

        return response.data().stream()
                .map(OpenAiModel::id)
                .filter(id -> id != null && !id.isBlank())
                .filter(OpenAiModelsService::isChatModelId)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
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
        if (request != null && request.getApiKey() != null && !request.getApiKey().isBlank()) {
            return request.getApiKey().trim();
        }
        if (request != null && !request.isUseSavedKey()) {
            return "";
        }

        ApplicationSettings settings = settingsService.getWithSecrets();
        String provider = request == null ? "openai" : Strings.defaultIfBlank(request.getProvider(), "openai");
        return switch (provider) {
            case "openai-official-sdk" -> Strings.defaultIfBlank(settings.getOpenAiOfficialSdk().getApiKey(), "");
            default -> Strings.defaultIfBlank(settings.getOpenAi().getApiKey(), "");
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiModelsResponse(List<OpenAiModel> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiModel(String id) {
    }
}
