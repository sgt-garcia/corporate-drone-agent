package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.GoogleGeminiModelsRequest;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.util.Strings;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GoogleGeminiModelsService {

    private final SettingsService settingsService;
    private final RestClient restClient;

    public GoogleGeminiModelsService(SettingsService settingsService, RestClient.Builder restClientBuilder) {
        this.settingsService = settingsService;
        this.restClient = restClientBuilder
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
    }

    public List<String> listModels(GoogleGeminiModelsRequest request) {
        String apiKey = apiKeyFor(request);
        if (apiKey.isBlank()) {
            return List.of();
        }

        JsonNode response;
        try {
            response = restClient.get()
                    .uri("/v1beta/models")
                    .header("x-goog-api-key", apiKey)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Google Gemini models request failed: "
                            + exception.getStatusCode().value()
                            + " "
                            + exception.getStatusText()
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google Gemini models request failed.");
        }

        if (response == null || !response.path("models").isArray()) {
            return List.of();
        }

        return StreamSupport.stream(response.path("models").spliterator(), false)
                .filter(GoogleGeminiModelsService::isChatModel)
                .map(model -> normalizeModelId(model.path("name").asText("")))
                .filter(id -> !id.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    static boolean isChatModel(JsonNode model) {
        if (model == null) {
            return false;
        }

        String id = normalizeModelId(model == null ? "" : model.path("name").asText(""));
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

    private String apiKeyFor(GoogleGeminiModelsRequest request) {
        if (request != null && request.getApiKey() != null && !request.getApiKey().isBlank()) {
            return request.getApiKey().trim();
        }
        if (request != null && !request.isUseSavedKey()) {
            return "";
        }

        ApplicationSettings settings = settingsService.getWithSecrets();
        return Strings.defaultIfBlank(settings.getGoogleGemini().getApiKey(), "");
    }
}
