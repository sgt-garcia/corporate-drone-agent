package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.JiraProjectDto;
import ai.corporatedroneagent.util.Strings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JiraProjectDiscoveryService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public JiraProjectDiscoveryService(ObjectMapper objectMapper) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(REQUEST_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                objectMapper
        );
    }

    JiraProjectDiscoveryService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public List<JiraProjectDto> searchProjects(
            String instanceUrl,
            String email,
            String token,
            String query,
            int maxResults
    ) {
        String trimmedQuery = Strings.defaultIfBlank(query, "").trim();
        String path = "/rest/api/3/project/search?maxResults=" + Math.max(1, maxResults);
        if (!trimmedQuery.isBlank()) {
            path += "&query=" + urlEncode(trimmedQuery);
        }
        JsonNode response = getJson(instanceUrl, email, token, path, "Jira project search");
        JsonNode values = response.path("values");
        if (!values.isArray()) {
            values = response.isArray() ? response : objectMapper.createArrayNode();
        }
        List<JiraProjectDto> projects = new ArrayList<>();
        for (JsonNode project : values) {
            toProject(project, "").ifPresent(projects::add);
        }
        return projects;
    }

    public JiraProjectDto getProject(String instanceUrl, String email, String token, String key) {
        String normalizedKey = Strings.defaultIfBlank(key, "").trim().toUpperCase(Locale.ROOT);
        if (normalizedKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jira project key is required");
        }
        JsonNode response = getJson(
                instanceUrl,
                email,
                token,
                "/rest/api/3/project/" + urlEncodePathSegment(normalizedKey),
                "Jira project lookup"
        );
        return toProject(response, "just now")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jira project not found"));
    }

    private JsonNode getJson(String instanceUrl, String email, String token, String path, String requestName) {
        HttpRequest request = HttpRequest.newBuilder(jiraUri(instanceUrl, path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", basicAuth(email, token))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return objectMapper.readTree(response.body());
            }
            if (status == HttpStatus.UNAUTHORIZED.value()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Jira rejected the saved credentials");
            }
            if (status == HttpStatus.FORBIDDEN.value()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Jira does not allow this account to browse projects");
            }
            if (status == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Jira project not found");
            }
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    requestName + " failed with HTTP " + status
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, requestName + " request failed");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, requestName + " request was interrupted");
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jira instance URL is invalid");
        }
    }

    private java.util.Optional<JiraProjectDto> toProject(JsonNode node, String checked) {
        String key = Strings.defaultIfBlank(node.path("key").asText(""), "").trim();
        String name = Strings.defaultIfBlank(node.path("name").asText(""), "").trim();
        if (key.isBlank() || name.isBlank()) {
            return java.util.Optional.empty();
        }
        JiraProjectDto project = new JiraProjectDto();
        project.setId(Strings.defaultIfBlank(node.path("id").asText(""), "jira-" + key.toLowerCase(Locale.ROOT)));
        project.setKey(key);
        project.setName(name);
        project.setStatus("scanned");
        project.setIssues(Math.max(0L, node.path("insight").path("totalIssueCount").asLong(0L)));
        project.setChecked(checked);
        return java.util.Optional.of(project);
    }

    private URI jiraUri(String instanceUrl, String path) {
        String base = instanceUrl.endsWith("/") ? instanceUrl.substring(0, instanceUrl.length() - 1) : instanceUrl;
        return URI.create(base + path);
    }

    private String basicAuth(String email, String token) {
        String credentials = email + ":" + token;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String urlEncodePathSegment(String value) {
        return urlEncode(value).replace("+", "%20");
    }
}
