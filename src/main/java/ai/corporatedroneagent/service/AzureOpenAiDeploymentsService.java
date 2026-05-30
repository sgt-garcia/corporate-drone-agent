package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.AzureOpenAiDeploymentsRequest;
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
public class AzureOpenAiDeploymentsService {

    private static final String DEPLOYMENTS_API_VERSION = "2023-05-15";

    private final SettingsService settingsService;
    private final RestClient.Builder restClientBuilder;

    public AzureOpenAiDeploymentsService(SettingsService settingsService, RestClient.Builder restClientBuilder) {
        this.settingsService = settingsService;
        this.restClientBuilder = restClientBuilder;
    }

    public List<String> listDeployments(AzureOpenAiDeploymentsRequest request) {
        String endpoint = endpointFor(request);
        String apiKey = apiKeyFor(request);
        if (endpoint.isBlank() || apiKey.isBlank()) {
            return List.of();
        }

        JsonNode response;
        try {
            response = restClientBuilder
                    .baseUrl(trimTrailingSlashes(endpoint))
                    .build()
                    .get()
                    .uri("/openai/deployments?api-version=" + DEPLOYMENTS_API_VERSION)
                    .header("api-key", apiKey)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Azure OpenAI deployments request failed: "
                            + exception.getStatusCode().value()
                            + " "
                            + exception.getStatusText()
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Azure OpenAI deployments request failed.");
        }

        JsonNode deployments = deploymentsNode(response);
        if (!deployments.isArray()) {
            return List.of();
        }

        return StreamSupport.stream(deployments.spliterator(), false)
                .filter(AzureOpenAiDeploymentsService::isChatDeployment)
                .map(AzureOpenAiDeploymentsService::deploymentName)
                .filter(name -> !name.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    static JsonNode deploymentsNode(JsonNode response) {
        if (response == null) {
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
        if (response.path("data").isArray()) {
            return response.path("data");
        }
        return response.path("value");
    }

    static String deploymentName(JsonNode deployment) {
        if (deployment == null) {
            return "";
        }

        String id = deployment.path("id").asText("");
        if (!id.isBlank()) {
            return id;
        }

        String name = deployment.path("name").asText("");
        if (!name.isBlank()) {
            return name;
        }

        return deployment.path("deploymentName").asText("");
    }

    static boolean isChatDeployment(JsonNode deployment) {
        String model = modelName(deployment).toLowerCase();
        if (model.isBlank()) {
            return OpenAiModelsService.isChatModelId(deploymentName(deployment));
        }
        return OpenAiModelsService.isChatModelId(model);
    }

    static String modelName(JsonNode deployment) {
        if (deployment == null) {
            return "";
        }

        String model = deployment.path("model").asText("");
        if (!model.isBlank()) {
            return model;
        }

        model = deployment.path("properties").path("model").path("name").asText("");
        if (!model.isBlank()) {
            return model;
        }

        return deployment.path("properties").path("model").path("format").asText("");
    }

    static String trimTrailingSlashes(String endpoint) {
        return endpoint.replaceAll("/+$", "");
    }

    private String endpointFor(AzureOpenAiDeploymentsRequest request) {
        String submittedEndpoint = request == null ? "" : request.getEndpoint();
        if (submittedEndpoint != null && !submittedEndpoint.isBlank()) {
            return submittedEndpoint.trim();
        }

        ApplicationSettings settings = settingsService.getWithSecrets();
        return Strings.defaultIfBlank(settings.getAzureOpenAi().getEndpoint(), "");
    }

    private String apiKeyFor(AzureOpenAiDeploymentsRequest request) {
        if (request != null && request.getApiKey() != null && !request.getApiKey().isBlank()) {
            return request.getApiKey().trim();
        }
        if (request != null && !request.isUseSavedKey()) {
            return "";
        }

        ApplicationSettings settings = settingsService.getWithSecrets();
        return Strings.defaultIfBlank(settings.getAzureOpenAi().getApiKey(), "");
    }
}
