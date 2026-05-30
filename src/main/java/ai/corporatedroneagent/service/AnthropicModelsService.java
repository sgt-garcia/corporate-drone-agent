package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.AnthropicModelsRequest;
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
public class AnthropicModelsService {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final SettingsService settingsService;
    private final RestClient restClient;

    public AnthropicModelsService(SettingsService settingsService, RestClient.Builder restClientBuilder) {
        this.settingsService = settingsService;
        this.restClient = restClientBuilder
                .baseUrl("https://api.anthropic.com")
                .build();
    }

    public List<String> listModels(AnthropicModelsRequest request) {
        String apiKey = apiKeyFor(request);
        if (apiKey.isBlank()) {
            return List.of();
        }

        JsonNode response;
        try {
            response = restClient.get()
                    .uri("/v1/models?limit=1000")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Anthropic models request failed: "
                            + exception.getStatusCode().value()
                            + " "
                            + exception.getStatusText()
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Anthropic models request failed.");
        }

        if (response == null || !response.path("data").isArray()) {
            return List.of();
        }

        return StreamSupport.stream(response.path("data").spliterator(), false)
                .map(model -> model.path("id").asText(""))
                .filter(AnthropicModelsService::isChatModelId)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    static boolean isChatModelId(String id) {
        return id != null && id.toLowerCase().startsWith("claude-");
    }

    private String apiKeyFor(AnthropicModelsRequest request) {
        if (request != null && request.getApiKey() != null && !request.getApiKey().isBlank()) {
            return request.getApiKey().trim();
        }
        if (request != null && !request.isUseSavedKey()) {
            return "";
        }

        ApplicationSettings settings = settingsService.getWithSecrets();
        return Strings.defaultIfBlank(settings.getAnthropic().getApiKey(), "");
    }
}
