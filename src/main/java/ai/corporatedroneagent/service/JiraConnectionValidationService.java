package ai.corporatedroneagent.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JiraConnectionValidationService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;

    public JiraConnectionValidationService() {
        this(HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    JiraConnectionValidationService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public ValidationResult validate(String instanceUrl, String email, String token) {
        ValidationResult cloudResult = validateMyself(instanceUrl, email, token, "/rest/api/3/myself");
        if (cloudResult.valid() || cloudResult.statusCode() != HttpStatus.NOT_FOUND.value()) {
            return cloudResult;
        }
        return validateMyself(instanceUrl, email, token, "/rest/api/2/myself");
    }

    private ValidationResult validateMyself(String instanceUrl, String email, String token, String path) {
        HttpRequest request = HttpRequest.newBuilder(myselfUri(instanceUrl, path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", basicAuth(email, token))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return new ValidationResult(true, "Jira credentials validated.", true, status);
            }
            if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
                return new ValidationResult(false, "Jira rejected the email or API token.", true, status);
            }
            if (status == HttpStatus.NOT_FOUND.value()) {
                return new ValidationResult(false, "Jira user endpoint was not found.", true, status);
            }
            return new ValidationResult(false, "Jira validation failed with HTTP " + status + ".", true, status);
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

    private String basicAuth(String email, String token) {
        String credentials = email + ":" + token;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    public record ValidationResult(
            boolean valid,
            String message,
            boolean liveValidationAvailable,
            int statusCode
    ) {
    }
}
