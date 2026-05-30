package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.OllamaModelsRequest;
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
public class OllamaModelsService {

    private final RestClient.Builder restClientBuilder;

    public OllamaModelsService(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    public List<String> listModels(OllamaModelsRequest request) {
        String baseUrl = Strings.defaultIfBlank(request == null ? "" : request.getBaseUrl(), "");
        if (baseUrl.isBlank()) {
            return List.of();
        }

        JsonNode response;
        try {
            response = restClientBuilder
                    .baseUrl(trimTrailingSlashes(baseUrl))
                    .build()
                    .get()
                    .uri("/api/tags")
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Ollama models request failed: "
                            + exception.getStatusCode().value()
                            + " "
                            + exception.getStatusText()
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ollama models request failed.");
        }

        if (response == null || !response.path("models").isArray()) {
            return List.of();
        }

        return StreamSupport.stream(response.path("models").spliterator(), false)
                .map(OllamaModelsService::modelName)
                .filter(name -> !name.isBlank())
                .filter(OllamaModelsService::isChatModelId)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    static String modelName(JsonNode model) {
        String name = model == null ? "" : model.path("model").asText("");
        if (name.isBlank()) {
            name = model == null ? "" : model.path("name").asText("");
        }
        return name;
    }

    static boolean isChatModelId(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }

        String normalizedModel = model.toLowerCase();
        return !normalizedModel.contains("embed");
    }

    static String trimTrailingSlashes(String baseUrl) {
        return baseUrl.replaceAll("/+$", "");
    }
}
