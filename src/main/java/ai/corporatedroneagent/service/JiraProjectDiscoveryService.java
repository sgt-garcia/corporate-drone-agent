package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.JiraProjectDto;
import ai.corporatedroneagent.util.Strings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JiraProjectDiscoveryService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int PROJECT_PAGE_SIZE = 50;
    private static final int MAX_PROJECT_PAGES = 20;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public JiraProjectDiscoveryService(ObjectMapper objectMapper) {
        this(
                AtlassianHttp.newHttpClient(REQUEST_TIMEOUT),
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
        return searchProjects(instanceUrl, email, token, query, maxResults, "3");
    }

    public List<JiraProjectDto> searchProjects(
            String instanceUrl,
            String email,
            String token,
            String query,
            int maxResults,
            String apiVersion
    ) {
        String trimmedQuery = Strings.defaultIfBlank(query, "").trim();
        int limit = Math.max(1, maxResults);
        String normalizedApiVersion = normalizeApiVersion(apiVersion);
        // The picker browses the whole instance and filters client-side, so a blank query
        // returns every project rather than the API's first page. Cloud caps a search page at
        // 50, so the full list must be paged.
        if (!"2".equals(normalizedApiVersion) && trimmedQuery.isBlank()) {
            return browseAllProjects(instanceUrl, email, token);
        }
        String path = "/rest/api/" + normalizedApiVersion + "/project/search?maxResults=" + limit;
        if (!trimmedQuery.isBlank()) {
            path += "&query=" + AtlassianHttp.urlEncode(trimmedQuery);
        }
        JsonNode response = getJson(
                instanceUrl,
                email,
                token,
                "2".equals(normalizedApiVersion) ? "/rest/api/2/project" : path,
                "Jira project search"
        );
        List<JiraProjectDto> projects = parseProjects(response);
        if (!"2".equals(normalizedApiVersion)) {
            return projects;
        }
        // Jira Server (v2) has no project-search query, so it returns every project and filters
        // locally. A blank query is the picker browsing the whole instance to filter client-side,
        // so it must return all — capping it would hide projects past the cap from the picker.
        return projects.stream()
                .filter(project -> trimmedQuery.isBlank()
                        || (project.getKey() + " " + project.getName()).toLowerCase(Locale.ROOT)
                        .contains(trimmedQuery.toLowerCase(Locale.ROOT)))
                .limit(trimmedQuery.isBlank() ? Long.MAX_VALUE : limit)
                .toList();
    }

    // Pages /project/search to gather the instance's full project list. Stops at the last page,
    // a short page, or a non-paginated (bare array) response from older deployments.
    private List<JiraProjectDto> browseAllProjects(String instanceUrl, String email, String token) {
        List<JiraProjectDto> projects = new ArrayList<>();
        int startAt = 0;
        for (int page = 0; page < MAX_PROJECT_PAGES; page++) {
            JsonNode response = getJson(
                    instanceUrl,
                    email,
                    token,
                    "/rest/api/3/project/search?startAt=" + startAt + "&maxResults=" + PROJECT_PAGE_SIZE,
                    "Jira project search"
            );
            boolean paged = response.path("values").isArray();
            projects.addAll(parseProjects(response));
            int returned = (paged ? response.path("values") : response).size();
            if (!paged || returned < PROJECT_PAGE_SIZE || response.path("isLast").asBoolean(false)) {
                break;
            }
            startAt += returned;
        }
        return projects;
    }

    private List<JiraProjectDto> parseProjects(JsonNode response) {
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
        return getProject(instanceUrl, email, token, key, "3");
    }

    public JiraProjectDto getProject(String instanceUrl, String email, String token, String key, String apiVersion) {
        String normalizedKey = Strings.defaultIfBlank(key, "").trim().toUpperCase(Locale.ROOT);
        if (normalizedKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jira project key is required");
        }
        String normalizedApiVersion = normalizeApiVersion(apiVersion);
        JsonNode response = getJson(
                instanceUrl,
                email,
                token,
                "/rest/api/" + normalizedApiVersion + "/project/" + AtlassianHttp.urlEncodePathSegment(normalizedKey),
                "Jira project lookup"
        );
        return toProject(response, "just now")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jira project not found"));
    }

    private JsonNode getJson(String instanceUrl, String email, String token, String path, String requestName) {
        HttpRequest request = HttpRequest.newBuilder(jiraUri(instanceUrl, path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", AtlassianHttp.basicAuth(email, token))
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

    private String normalizeApiVersion(String apiVersion) {
        return "2".equals(Strings.defaultIfBlank(apiVersion, "").trim()) ? "2" : "3";
    }
}
