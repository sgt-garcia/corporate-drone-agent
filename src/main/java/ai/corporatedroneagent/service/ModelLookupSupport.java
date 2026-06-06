package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.util.Strings;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModelLookupSupport {

    private final SettingsService settingsService;

    public ModelLookupSupport(SettingsService settingsService) {
        this.settingsService = settingsService;
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
}
