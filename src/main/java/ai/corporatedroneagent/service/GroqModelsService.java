package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.GroqModelsRequest;
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
public class GroqModelsService {

    private final SettingsService settingsService;
    private final RestClient restClient;

    public GroqModelsService(SettingsService settingsService, RestClient.Builder restClientBuilder) {
        this.settingsService = settingsService;
        this.restClient = restClientBuilder
                .baseUrl("https://api.groq.com/openai")
                .build();
    }

    public List<String> listModels(GroqModelsRequest request) {
        String apiKey = apiKeyFor(request);
        if (apiKey.isBlank()) {
            return List.of();
        }

        GroqModelsResponse response;
        try {
            response = restClient.get()
                    .uri("/v1/models")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .body(GroqModelsResponse.class);
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Groq models request failed: "
                            + exception.getStatusCode().value()
                            + " "
                            + exception.getStatusText()
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Groq models request failed.");
        }

        if (response == null || response.data() == null) {
            return List.of();
        }

        return response.data().stream()
                .map(GroqModel::id)
                .filter(GroqModelsService::isChatModelId)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
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

    private String apiKeyFor(GroqModelsRequest request) {
        if (request != null && request.getApiKey() != null && !request.getApiKey().isBlank()) {
            return request.getApiKey().trim();
        }
        if (request != null && !request.isUseSavedKey()) {
            return "";
        }

        ApplicationSettings settings = settingsService.getWithSecrets();
        return Strings.defaultIfBlank(settings.getGroq().getApiKey(), "");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GroqModelsResponse(List<GroqModel> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GroqModel(String id) {
    }
}
