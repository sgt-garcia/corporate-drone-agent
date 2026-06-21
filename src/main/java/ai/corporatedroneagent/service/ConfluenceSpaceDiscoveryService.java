package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.ConfluenceSpaceDto;
import ai.corporatedroneagent.model.knowledge.ConfluenceKnowledgeReferences;
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

/**
 * Browses Confluence spaces for the Settings picker. Confluence Cloud's space listing has
 * no server-side name query, so {@link #searchSpaces} pages the global spaces and filters
 * on key/name locally — mirroring {@link JiraProjectDiscoveryService}'s v2 fallback.
 */
@Service
public class ConfluenceSpaceDiscoveryService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int SPACE_PAGE_SIZE = 100;
    private static final int MAX_SPACE_PAGES = 10;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ConfluenceSpaceDiscoveryService(ObjectMapper objectMapper) {
        this(
                AtlassianHttp.newHttpClient(REQUEST_TIMEOUT),
                objectMapper
        );
    }

    ConfluenceSpaceDiscoveryService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public List<ConfluenceSpaceDto> searchSpaces(
            String instanceUrl,
            String email,
            String token,
            String query,
            int maxResults
    ) {
        String trimmedQuery = Strings.defaultIfBlank(query, "").trim().toLowerCase(Locale.ROOT);
        int limit = Math.max(1, maxResults);
        List<ConfluenceSpaceDto> spaces = new ArrayList<>();

        int start = 0;
        for (int page = 0; page < MAX_SPACE_PAGES; page++) {
            JsonNode response = getJson(
                    instanceUrl,
                    email,
                    token,
                    "/rest/api/space?type=global&status=current&start=" + start + "&limit=" + SPACE_PAGE_SIZE,
                    "Confluence space search"
            );
            JsonNode results = response.path("results");
            if (!results.isArray() || results.isEmpty()) {
                break;
            }
            for (JsonNode space : results) {
                toSpace(space, "").ifPresent(spaces::add);
            }
            int size = response.path("size").asInt(results.size());
            start += size;
            if (size < SPACE_PAGE_SIZE || response.path("_links").path("next").asText("").isBlank()) {
                break;
            }
        }

        return spaces.stream()
                .filter(space -> trimmedQuery.isBlank()
                        || (space.getKey() + " " + space.getName()).toLowerCase(Locale.ROOT).contains(trimmedQuery))
                .limit(limit)
                .toList();
    }

    public ConfluenceSpaceDto getSpace(String instanceUrl, String email, String token, String key) {
        String normalizedKey = Strings.defaultIfBlank(key, "").trim().toUpperCase(Locale.ROOT);
        if (normalizedKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confluence space key is required");
        }
        JsonNode response = getJson(
                instanceUrl,
                email,
                token,
                "/rest/api/space/" + AtlassianHttp.urlEncodePathSegment(normalizedKey),
                "Confluence space lookup"
        );
        return toSpace(response, "just now")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Confluence space not found"));
    }

    private JsonNode getJson(String instanceUrl, String email, String token, String path, String requestName) {
        HttpRequest request = HttpRequest.newBuilder(confluenceUri(instanceUrl, path))
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
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Confluence rejected the saved credentials");
            }
            if (status == HttpStatus.FORBIDDEN.value()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Confluence does not allow this account to browse spaces");
            }
            if (status == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Confluence space not found");
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confluence instance URL is invalid");
        }
    }

    private java.util.Optional<ConfluenceSpaceDto> toSpace(JsonNode node, String checked) {
        String key = Strings.defaultIfBlank(node.path("key").asText(""), "").trim();
        String name = Strings.defaultIfBlank(node.path("name").asText(""), "").trim();
        if (key.isBlank() || name.isBlank()) {
            return java.util.Optional.empty();
        }
        ConfluenceSpaceDto space = new ConfluenceSpaceDto();
        space.setId(Strings.defaultIfBlank(node.path("id").asText(""), "confluence-" + key.toLowerCase(Locale.ROOT)));
        space.setKey(key);
        space.setName(name);
        space.setStatus("scanned");
        space.setPages(0);
        space.setChecked(checked);
        return java.util.Optional.of(space);
    }

    private URI confluenceUri(String instanceUrl, String path) {
        return URI.create(ConfluenceKnowledgeReferences.apiBaseUrl(instanceUrl) + path);
    }
}
