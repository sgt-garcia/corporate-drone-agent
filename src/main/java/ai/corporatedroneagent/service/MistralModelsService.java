package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.MistralModelsRequest;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.util.Strings;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MistralModelsService {

    private final SettingsService settingsService;
    private final RestClient restClient;

    public MistralModelsService(SettingsService settingsService, RestClient.Builder restClientBuilder) {
        this.settingsService = settingsService;
        this.restClient = restClientBuilder
                .baseUrl("https://api.mistral.ai")
                .build();
    }

    public List<String> listModels(MistralModelsRequest request) {
        String apiKey = apiKeyFor(request);
        if (apiKey.isBlank()) {
            return List.of();
        }

        JsonNode response;
        try {
            response = restClient.get()
                    .uri("/v1/models")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Mistral models request failed: "
                            + exception.getStatusCode().value()
                            + " "
                            + exception.getStatusText()
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Mistral models request failed.");
        }

        if (response == null) {
            return List.of();
        }

        JsonNode data = response.isArray() ? response : response.path("data");
        if (!data.isArray()) {
            return List.of();
        }

        return StreamSupport.stream(data.spliterator(), false)
                .filter(MistralModelsService::isChatModel)
                .map(model -> model.path("id").asText(""))
                .filter(id -> !id.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    static boolean isChatModel(JsonNode model) {
        return model != null
                && model.path("capabilities").path("completion_chat").asBoolean(false)
                && !model.path("archived").asBoolean(false);
    }

    private String apiKeyFor(MistralModelsRequest request) {
        if (request != null && request.getApiKey() != null && !request.getApiKey().isBlank()) {
            return request.getApiKey().trim();
        }
        if (request != null && !request.isUseSavedKey()) {
            return "";
        }

        ApplicationSettings settings = settingsService.getWithSecrets();
        return Strings.defaultIfBlank(settings.getMistralAi().getApiKey(), "");
    }
}
