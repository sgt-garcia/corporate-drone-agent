package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.ApiKeyModelsRequest;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.util.Strings;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModelLookupSupport {

    private final SettingsService settingsService;

    public ModelLookupSupport(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public String apiKey(ApiKeyModelsRequest request, Function<ApplicationSettings, String> savedApiKey) {
        return apiKey(
                request == null ? "" : request.getApiKey(),
                request == null || request.isUseSavedKey(),
                savedApiKey
        );
    }

    public String apiKey(
            String submittedApiKey,
            boolean useSavedKey,
            Function<ApplicationSettings, String> savedApiKey
    ) {
        if (submittedApiKey != null && !submittedApiKey.isBlank()) {
            return submittedApiKey.trim();
        }
        if (!useSavedKey) {
            return "";
        }

        ApplicationSettings settings = settingsService.getWithSecrets();
        return Strings.defaultIfBlank(savedApiKey.apply(settings), "");
    }

    public String savedSetting(Function<ApplicationSettings, String> savedSetting) {
        ApplicationSettings settings = settingsService.getWithSecrets();
        return Strings.defaultIfBlank(savedSetting.apply(settings), "");
    }

    public <T> T request(String requestName, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    requestName
                            + " request failed: "
                            + exception.getStatusCode().value()
                            + " "
                            + exception.getStatusText()
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, requestName + " request failed.");
        }
    }

    public List<String> sortedDistinct(Stream<String> values) {
        return values
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    public Stream<JsonNode> elements(JsonNode array) {
        if (array == null || !array.isArray()) {
            return Stream.empty();
        }
        return StreamSupport.stream(array.spliterator(), false);
    }

    /**
     * Lists chat model ids from an OpenAI-compatible {@code GET} that returns
     * {@code {"data": [{"id": ...}]}} behind a {@code Bearer} token. Shared by the OpenAI,
     * Groq, and DeepSeek services, which differ only in base URL, path, and chat-id filter.
     */
    public List<String> listBearerModels(
            RestClient restClient,
            String requestName,
            String path,
            String apiKey,
            Predicate<String> isChatModelId
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            return List.of();
        }
        JsonNode response = request(
                requestName,
                () -> restClient.get()
                        .uri(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .retrieve()
                        .body(JsonNode.class)
        );
        JsonNode data = response == null ? null : response.path("data");
        return sortedDistinct(elements(data)
                .map(node -> node.path("id").asText(null))
                .filter(id -> id != null && isChatModelId.test(id)));
    }
}
