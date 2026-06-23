package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.ConfluenceSpaceDto;
import ai.corporatedroneagent.model.knowledge.ConfluenceKnowledgeReferences;
import ai.corporatedroneagent.util.Strings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ConfluenceSpaceDiscoveryService.class);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int SPACE_PAGE_SIZE = 100;
    // Runaway guard only — paging normally stops on a short page / no "next" link. Set far above
    // any real instance (Confluence's hard ceiling is ~131k spaces) so every space loads.
    private static final int MAX_SPACE_PAGES = 2_000;

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
        boolean reachedEnd = false;
        for (int page = 0; page < MAX_SPACE_PAGES && !reachedEnd; page++) {
            JsonNode response = getJson(
                    instanceUrl,
                    email,
                    token,
                    "/rest/api/space?type=global&status=current&start=" + start + "&limit=" + SPACE_PAGE_SIZE,
                    "Confluence space search"
            );
            JsonNode results = response.path("results");
            if (!results.isArray() || results.isEmpty()) {
                reachedEnd = true;
                break;
            }
            for (JsonNode space : results) {
                toSpace(space, "").ifPresent(spaces::add);
            }
            int size = response.path("size").asInt(results.size());
            start += size;
            if (size < SPACE_PAGE_SIZE || response.path("_links").path("next").asText("").isBlank()) {
                reachedEnd = true;
            }
        }
        if (!reachedEnd) {
            log.warn("Confluence space browse hit the {}-page guard; some spaces may be omitted.", MAX_SPACE_PAGES);
        }

        // The picker browses the whole instance and filters client-side, so a blank query
        // returns every space; a non-blank query keeps the capped server-side fallback.
        return spaces.stream()
                .filter(space -> trimmedQuery.isBlank()
                        || (space.key() + " " + space.name()).toLowerCase(Locale.ROOT).contains(trimmedQuery))
                .limit(trimmedQuery.isBlank() ? Long.MAX_VALUE : limit)
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
        return AtlassianHttp.getJson(
                httpClient, objectMapper, REQUEST_TIMEOUT, confluenceUri(instanceUrl, path), email, token,
                new AtlassianHttp.RequestErrors(
                        requestName,
                        "Confluence rejected the saved credentials",
                        "Confluence does not allow this account to browse spaces",
                        "Confluence space not found",
                        "Confluence instance URL is invalid"));
    }

    private java.util.Optional<ConfluenceSpaceDto> toSpace(JsonNode node, String checked) {
        String key = Strings.defaultIfBlank(node.path("key").asText(""), "").trim();
        String name = Strings.defaultIfBlank(node.path("name").asText(""), "").trim();
        if (key.isBlank() || name.isBlank()) {
            return java.util.Optional.empty();
        }
        ConfluenceSpaceDto space = new ConfluenceSpaceDto(
                Strings.defaultIfBlank(node.path("id").asText(""), "confluence-" + key.toLowerCase(Locale.ROOT)),
                key,
                name,
                "scanned",
                0,
                checked,
                "");
        return java.util.Optional.of(space);
    }

    private URI confluenceUri(String instanceUrl, String path) {
        return URI.create(ConfluenceKnowledgeReferences.apiBaseUrl(instanceUrl) + path);
    }
}
