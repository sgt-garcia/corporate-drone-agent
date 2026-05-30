package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.DeepSeekModelsRequest;
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
public class DeepSeekModelsService {

    private final SettingsService settingsService;
    private final RestClient restClient;

    public DeepSeekModelsService(SettingsService settingsService, RestClient.Builder restClientBuilder) {
        this.settingsService = settingsService;
        this.restClient = restClientBuilder
                .baseUrl("https://api.deepseek.com")
                .build();
    }

    public List<String> listModels(DeepSeekModelsRequest request) {
        String apiKey = apiKeyFor(request);
        if (apiKey.isBlank()) {
            return List.of();
        }

        DeepSeekModelsResponse response;
        try {
            response = restClient.get()
                    .uri("/models")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .body(DeepSeekModelsResponse.class);
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "DeepSeek models request failed: "
                            + exception.getStatusCode().value()
                            + " "
                            + exception.getStatusText()
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DeepSeek models request failed.");
        }

        if (response == null || response.data() == null) {
            return List.of();
        }

        return response.data().stream()
                .map(DeepSeekModel::id)
                .filter(DeepSeekModelsService::isChatModelId)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    static boolean isChatModelId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        String normalizedId = id.toLowerCase();
        return normalizedId.startsWith("deepseek-")
                && !normalizedId.contains("embedding")
                && !normalizedId.contains("fim");
    }

    private String apiKeyFor(DeepSeekModelsRequest request) {
        if (request != null && request.getApiKey() != null && !request.getApiKey().isBlank()) {
            return request.getApiKey().trim();
        }
        if (request != null && !request.isUseSavedKey()) {
            return "";
        }

        ApplicationSettings settings = settingsService.getWithSecrets();
        return Strings.defaultIfBlank(settings.getDeepSeek().getApiKey(), "");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekModelsResponse(List<DeepSeekModel> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeepSeekModel(String id) {
    }
}
