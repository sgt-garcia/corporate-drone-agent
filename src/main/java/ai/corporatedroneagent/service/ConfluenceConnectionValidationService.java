package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.ConfluenceKnowledgeReferences;
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

/**
 * Live credential check for Confluence Cloud. Hits the spaces listing (the cheapest
 * authenticated read) with the supplied basic-auth credentials; a 2xx means the wiki base
 * URL and the email/token pair are good. Sibling of {@link JiraConnectionValidationService}
 * — Confluence Cloud exposes a single REST API, so there's no version probe.
 */
@Service
public class ConfluenceConnectionValidationService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;

    public ConfluenceConnectionValidationService() {
        this(HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    ConfluenceConnectionValidationService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public ValidationResult validate(String instanceUrl, String email, String token) {
        HttpRequest request = HttpRequest.newBuilder(spacesUri(instanceUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", basicAuth(email, token))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return new ValidationResult(true, "Confluence credentials validated.", true, status);
            }
            if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
                return new ValidationResult(false, "Confluence rejected the email or API token.", true, status);
            }
            if (status == HttpStatus.NOT_FOUND.value()) {
                return new ValidationResult(false, "Confluence REST endpoint was not found — check the wiki base URL.", true, status);
            }
            return new ValidationResult(false, "Confluence validation failed with HTTP " + status + ".", true, status);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach Confluence instance");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Confluence validation was interrupted");
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confluence instance URL is invalid");
        }
    }

    private URI spacesUri(String instanceUrl) {
        return URI.create(ConfluenceKnowledgeReferences.apiBaseUrl(instanceUrl) + "/rest/api/space?limit=1");
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
