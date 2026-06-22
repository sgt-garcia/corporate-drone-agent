package ai.corporatedroneagent.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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
        HttpResponse<String> response = AtlassianHttp.send(
                httpClient, REQUEST_TIMEOUT, myselfUri(instanceUrl, path), email, token,
                "Could not reach Jira instance",
                "Jira validation was interrupted",
                "Jira instance URL is invalid");
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return new ValidationResult(true, "Jira credentials validated.", status, apiVersion);
        }
        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            return new ValidationResult(false, "Jira rejected the email or API token.", status, apiVersion);
        }
        if (status == HttpStatus.NOT_FOUND.value()) {
            return new ValidationResult(false, "Jira user endpoint was not found.", status, apiVersion);
        }
        return new ValidationResult(false, "Jira validation failed with HTTP " + status + ".", status, apiVersion);
    }

    private URI myselfUri(String instanceUrl, String path) {
        String base = instanceUrl.endsWith("/") ? instanceUrl.substring(0, instanceUrl.length() - 1) : instanceUrl;
        return URI.create(base + path);
    }

    public record ValidationResult(
            boolean valid,
            String message,
            int statusCode,
            String apiVersion
    ) {
    }
}
