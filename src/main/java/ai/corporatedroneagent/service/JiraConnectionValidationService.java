package ai.corporatedroneagent.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JiraConnectionValidationService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;

    public JiraConnectionValidationService() {
        this(AtlassianHttp.newHttpClient(REQUEST_TIMEOUT));
    }

    JiraConnectionValidationService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public ValidationResult validate(String instanceUrl, String email, String token) {
        ValidationResult cloudResult = validateMyself(instanceUrl, email, token, "3");
        if (cloudResult.valid() || cloudResult.statusCode() != HttpStatus.NOT_FOUND.value()) {
            return cloudResult;
        }
        return validateMyself(instanceUrl, email, token, "2");
    }

    private ValidationResult validateMyself(String instanceUrl, String email, String token, String apiVersion) {
        String path = "/rest/api/" + apiVersion + "/myself";
        HttpRequest request = HttpRequest.newBuilder(myselfUri(instanceUrl, path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", AtlassianHttp.basicAuth(email, token))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return new ValidationResult(true, "Jira credentials validated.", true, status, apiVersion);
            }
            if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
                return new ValidationResult(false, "Jira rejected the email or API token.", true, status, apiVersion);
            }
            if (status == HttpStatus.NOT_FOUND.value()) {
                return new ValidationResult(false, "Jira user endpoint was not found.", true, status, apiVersion);
            }
            return new ValidationResult(false, "Jira validation failed with HTTP " + status + ".", true, status, apiVersion);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach Jira instance");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Jira validation was interrupted");
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jira instance URL is invalid");
        }
    }

    private URI myselfUri(String instanceUrl, String path) {
        String base = instanceUrl.endsWith("/") ? instanceUrl.substring(0, instanceUrl.length() - 1) : instanceUrl;
        return URI.create(base + path);
    }

    public record ValidationResult(
            boolean valid,
            String message,
            boolean liveValidationAvailable,
            int statusCode,
            String apiVersion
    ) {
    }
}
