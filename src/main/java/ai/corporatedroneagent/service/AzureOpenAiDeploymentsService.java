package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.AzureOpenAiDeploymentsRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AzureOpenAiDeploymentsService {

    private static final String DEPLOYMENTS_API_VERSION = "2023-05-15";
    private static final List<String> FALLBACK_DEPLOYMENTS_API_VERSIONS = List.of(
            DEPLOYMENTS_API_VERSION,
            "2023-03-15-preview"
    );

    private final ModelLookupSupport modelLookupSupport;
    private final RestClient.Builder restClientBuilder;

    public AzureOpenAiDeploymentsService(ModelLookupSupport modelLookupSupport, RestClient.Builder restClientBuilder) {
        this.modelLookupSupport = modelLookupSupport;
        this.restClientBuilder = restClientBuilder;
    }

    public List<String> listDeployments(AzureOpenAiDeploymentsRequest request) {
        String endpoint = endpointFor(request);
        String apiKey = apiKeyFor(request);
        if (endpoint.isBlank() || apiKey.isBlank()) {
            return List.of();
        }

        Optional<JsonNode> response;
        try {
            response = loadDeployments(endpoint, apiKey);
        } catch (RestClientResponseException exception) {
            if (isUnsupportedDeploymentListResponse(exception)) {
                return List.of();
            }
            throw azureDeploymentsException(exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Azure OpenAI deployments request failed.");
        }

        JsonNode deployments = deploymentsNode(response.orElse(null));
        if (!deployments.isArray()) {
            return List.of();
        }

        return modelLookupSupport.sortedDistinct(modelLookupSupport.elements(deployments)
                .filter(AzureOpenAiDeploymentsService::isChatDeployment)
                .map(AzureOpenAiDeploymentsService::deploymentName)
        );
    }

    private Optional<JsonNode> loadDeployments(String endpoint, String apiKey) {
        RestClient restClient = restClientBuilder
                .baseUrl(trimTrailingSlashes(endpoint))
                .build();

        for (String apiVersion : FALLBACK_DEPLOYMENTS_API_VERSIONS) {
            try {
                return Optional.ofNullable(restClient.get()
                        .uri("/openai/deployments?api-version=" + apiVersion)
                        .header("api-key", apiKey)
                        .retrieve()
                        .body(JsonNode.class));
            } catch (RestClientResponseException exception) {
                if (!isUnsupportedDeploymentListResponse(exception)) {
                    throw exception;
                }
            }
        }

        return Optional.empty();
    }

    private static boolean isUnsupportedDeploymentListResponse(RestClientResponseException exception) {
        int statusCode = exception.getStatusCode().value();
        return statusCode == 400 || statusCode == 404 || statusCode == 410;
    }

    private ResponseStatusException azureDeploymentsException(RestClientResponseException exception) {
        String responseBody = exception.getResponseBodyAsString();
        String details = responseBody == null || responseBody.isBlank()
                ? exception.getStatusText()
                : responseBody;
        if (details.length() > 500) {
            details = details.substring(0, 500) + "...";
        }

        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Azure OpenAI deployments request failed: "
                        + exception.getStatusCode().value()
                        + " "
                        + details
        );
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

        return modelLookupSupport.savedSetting(settings -> settings.getAzureOpenAi().getEndpoint());
    }

    private String apiKeyFor(AzureOpenAiDeploymentsRequest request) {
        return modelLookupSupport.apiKey(
                request == null ? "" : request.getApiKey(),
                request == null || request.isUseSavedKey(),
                settings -> settings.getAzureOpenAi().getApiKey()
        );
    }
}
