package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.ConfluenceKnowledgeReferences;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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
        this(AtlassianHttp.newHttpClient(REQUEST_TIMEOUT));
    }

    ConfluenceConnectionValidationService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public ValidationResult validate(String instanceUrl, String email, String token) {
        HttpResponse<String> response = AtlassianHttp.send(
                httpClient, REQUEST_TIMEOUT, spacesUri(instanceUrl), email, token,
                "Could not reach Confluence instance",
                "Confluence validation was interrupted",
                "Confluence instance URL is invalid");
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return new ValidationResult(true, "Confluence credentials validated.");
        }
        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            return new ValidationResult(false, "Confluence rejected the email or API token.");
        }
        if (status == HttpStatus.NOT_FOUND.value()) {
            return new ValidationResult(false, "Confluence REST endpoint was not found — check the wiki base URL.");
        }
        return new ValidationResult(false, "Confluence validation failed with HTTP " + status + ".");
    }

    private URI spacesUri(String instanceUrl) {
        return URI.create(ConfluenceKnowledgeReferences.apiBaseUrl(instanceUrl) + "/rest/api/space?limit=1");
    }

    public record ValidationResult(
            boolean valid,
            String message
    ) {
    }
}
