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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OpenAI API key is required to load models.");
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
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
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
