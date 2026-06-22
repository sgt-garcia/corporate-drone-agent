package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.OllamaModelsRequest;
import ai.corporatedroneagent.util.Strings;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OllamaModelsService {

    private final ModelLookupSupport modelLookupSupport;
    private final RestClient.Builder restClientBuilder;

    public OllamaModelsService(ModelLookupSupport modelLookupSupport, RestClient.Builder restClientBuilder) {
        this.modelLookupSupport = modelLookupSupport;
        this.restClientBuilder = restClientBuilder;
    }

    public List<String> listModels(OllamaModelsRequest request) {
        String baseUrl = Strings.defaultIfBlank(request == null ? "" : request.getBaseUrl(), "");
        if (baseUrl.isBlank()) {
            return List.of();
        }

        JsonNode response = modelLookupSupport.request(
                "Ollama models",
                () -> restClientBuilder
                    .baseUrl(Strings.trimTrailingSlashes(baseUrl))
                    .build()
                    .get()
                    .uri("/api/tags")
                    .retrieve()
                    .body(JsonNode.class)
        );

        return modelLookupSupport.sortedDistinct(modelLookupSupport.elements(response == null ? null : response.path("models"))
                .map(OllamaModelsService::modelName)
                .filter(OllamaModelsService::isChatModelId));
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
}
