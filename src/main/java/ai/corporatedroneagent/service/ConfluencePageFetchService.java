package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.ConfluenceSpaceDto;
import ai.corporatedroneagent.model.knowledge.ConfluenceKnowledgeReferences;
import ai.corporatedroneagent.util.Strings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Confluence-specific half of ingestion: enumerate a space's pages (optionally since a
 * cursor) via CQL, read one page's storage-format body, render it to markdown. Sibling of
 * {@link JiraIssueFetchService}. Confluence reports timestamp-only change detection and does
 * not reconcile deletions, since an incremental CQL enumeration only returns changed pages.
 */
@Service
public class ConfluencePageFetchService {

    static final int PAGE_SIZE = 50;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration DELTA_QUERY_TIMEZONE_SAFETY_OVERLAP = Duration.ofHours(24);
    private static final DateTimeFormatter CQL_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private static final int MAX_SEARCH_PAGES = 200;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ConfluencePageFetchService(ObjectMapper objectMapper) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(REQUEST_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                objectMapper
        );
    }

    ConfluencePageFetchService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public List<ConfluencePageManifest> fetchSpacePageManifests(
            String instanceUrl,
            String email,
            String token,
            ConfluenceSpaceDto space
    ) {
        return fetchSpacePageManifests(instanceUrl, email, token, space, null);
    }

    public List<ConfluencePageManifest> fetchSpacePageManifests(
            String instanceUrl,
            String email,
            String token,
            ConfluenceSpaceDto space,
            Instant updatedSince
    ) {
        String spaceKey = Strings.defaultIfBlank(space.getKey(), "").trim();
        if (spaceKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confluence space key is required");
        }

        List<ConfluencePageManifest> manifests = new ArrayList<>();
        String path = pageSearchPath(spaceKey, updatedSince);
        for (int page = 0; page < MAX_SEARCH_PAGES; page++) {
            JsonNode response = getJson(instanceUrl, email, token, path, "Confluence page search");
            JsonNode results = response.path("results");
            if (!results.isArray() || results.isEmpty()) {
                break;
            }
            for (JsonNode pageNode : results) {
                manifests.add(toPageManifest(instanceUrl, pageNode));
            }
            String next = nextSearchPath(response);
            if (next == null) {
                break;
            }
            path = next;
        }
        return manifests;
    }

    public ConfluencePageDocument fetchPageDocument(
            String instanceUrl,
            String email,
            String token,
            ConfluencePageManifest manifest
    ) {
        JsonNode page = getJson(
                instanceUrl,
                email,
                token,
                pagePath(manifest.id()),
                "Confluence page"
        );

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("source", "confluence");
        payload.put("fetchedAt", Instant.now().toString());
        payload.set("page", page);

        try {
            byte[] value = objectMapper.writeValueAsBytes(payload);
            String id = Strings.defaultIfBlank(page.path("id").asText(""), manifest.id()).trim();
            String title = Strings.defaultIfBlank(page.path("title").asText(""), manifest.title()).trim();
            return new ConfluencePageDocument(
                    id,
                    title,
                    ConfluenceKnowledgeReferences.pageResourceReference(instanceUrl, id),
                    title.isBlank() ? id : title,
                    "confluence-page",
                    value.length,
                    parseTimestamp(page.path("version").path("when").asText("")).orElse(manifest.lastModifiedAt()),
                    value
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not serialize Confluence page");
        }
    }

    public String toMarkdown(byte[] readValue) {
        try {
            JsonNode payload = objectMapper.readTree(readValue == null ? new byte[0] : readValue);
            return pageText(payload.path("page"));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not parse Confluence page payload");
        }
    }

    private ConfluencePageManifest toPageManifest(String instanceUrl, JsonNode page) {
        String id = Strings.defaultIfBlank(page.path("id").asText(""), "").trim();
        String title = Strings.defaultIfBlank(page.path("title").asText(""), "").trim();
        if (id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Confluence page search returned an invalid page");
        }
        return new ConfluencePageManifest(
                id,
                title,
                ConfluenceKnowledgeReferences.pageResourceReference(instanceUrl, id),
                title.isBlank() ? id : title,
                "confluence-page",
                manifestSizeBytes(id, title),
                parseTimestamp(page.path("version").path("when").asText("")).orElse(null)
        );
    }

    private String pageText(JsonNode page) {
        StringBuilder text = new StringBuilder();
        String title = Strings.defaultIfBlank(page.path("title").asText(""), "").trim();
        appendLine(text, "# " + (title.isBlank() ? "Untitled page" : title));
        appendField(text, "Space", spaceName(page.path("space")));
        appendField(text, "Breadcrumb", ancestors(page.path("ancestors")));
        appendField(text, "Version", versionLabel(page.path("version")));
        appendField(text, "Last updated", page.path("version").path("when").asText(""));
        appendField(text, "Updated by", page.path("version").path("by").path("displayName").asText(""));

        String body = stripStorageMarkup(page.path("body").path("storage").path("value").asText(""));
        if (!body.isBlank()) {
            appendLine(text, "");
            appendLine(text, "## Content");
            appendLine(text, "");
            appendLine(text, body);
        }
        return text.toString().strip();
    }

    private String pagePath(String pageId) {
        // `version` already carries the author (`by`) and `when`; a separate `version.by`
        // expand is redundant and rejected with HTTP 400 by some Confluence instances.
        return "/rest/api/content/" + urlEncodePathSegment(pageId)
                + "?expand=" + urlEncode("body.storage,version,space,ancestors");
    }

    private String pageSearchPath(String spaceKey, Instant updatedSince) {
        String cql = "space=" + cqlSpaceLiteral(spaceKey) + " and type=page";
        if (updatedSince != null) {
            Instant safeUpdatedSince = updatedSince.minus(DELTA_QUERY_TIMEZONE_SAFETY_OVERLAP);
            cql += " and lastModified >= \"" + CQL_TIMESTAMP.format(safeUpdatedSince.atOffset(ZoneOffset.UTC)) + "\"";
        }
        cql += " order by lastModified desc";
        return "/rest/api/content/search?cql=" + urlEncode(cql)
                + "&limit=" + PAGE_SIZE
                + "&expand=" + urlEncode("version");
    }

    // The CQL "next" cursor link Confluence returns is relative to the wiki context base, so it
    // already starts with /rest/...; instanceUrl carries the /wiki prefix. Strip a duplicated
    // /wiki and any absolute host so the link composes cleanly with confluenceUri().
    private String nextSearchPath(JsonNode response) {
        String next = response.path("_links").path("next").asText("");
        if (next.isBlank()) {
            return null;
        }
        if (next.startsWith("http")) {
            URI uri = URI.create(next);
            String path = Strings.defaultIfBlank(uri.getRawPath(), "");
            String query = uri.getRawQuery();
            next = query == null ? path : path + "?" + query;
        }
        if (next.startsWith("/wiki/")) {
            next = next.substring("/wiki".length());
        }
        return next.isBlank() ? null : next;
    }

    private long manifestSizeBytes(String id, String title) {
        return (id + "\n" + Strings.defaultIfBlank(title, "")).getBytes(StandardCharsets.UTF_8).length;
    }

    private String spaceName(JsonNode space) {
        if (space == null || space.isMissingNode() || space.isNull()) {
            return "";
        }
        String key = Strings.defaultIfBlank(space.path("key").asText(""), "").trim();
        String name = Strings.defaultIfBlank(space.path("name").asText(""), "").trim();
        if (key.isBlank()) {
            return name;
        }
        return name.isBlank() ? key : key + " - " + name;
    }

    private String ancestors(JsonNode ancestors) {
        if (!ancestors.isArray() || ancestors.isEmpty()) {
            return "";
        }
        List<String> titles = new ArrayList<>();
        for (JsonNode ancestor : ancestors) {
            String title = Strings.defaultIfBlank(ancestor.path("title").asText(""), "").trim();
            if (!title.isBlank()) {
                titles.add(title);
            }
        }
        return String.join(" / ", titles);
    }

    private String versionLabel(JsonNode version) {
        int number = version.path("number").asInt(0);
        return number > 0 ? "v" + number : "";
    }

    // Confluence storage format is XHTML with ac:/ri: macro tags. For indexing we only need
    // readable text: drop macro/structured-data tags, turn block elements into line breaks,
    // strip the rest, and decode the handful of entities the storage format emits.
    private String stripStorageMarkup(String storage) {
        String value = Strings.defaultIfBlank(storage, "");
        if (value.isBlank()) {
            return "";
        }
        // Macros and structured-macro internals rarely carry prose — drop their bodies.
        value = value.replaceAll("(?s)<ac:parameter.*?</ac:parameter>", " ");
        value = value.replaceAll("(?s)<ri:[^>]*?/?>", " ");
        // Block-level elements become line breaks.
        value = value.replaceAll("(?i)<br\\s*/?>", "\n");
        value = value.replaceAll("(?i)</(p|div|li|h[1-6]|tr|blockquote|td|th)>", "\n");
        value = value.replaceAll("(?i)<li[^>]*>", "- ");
        // Drop every remaining tag.
        value = value.replaceAll("(?s)<[^>]+>", "");
        value = value
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        value = value.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n");
        return value.strip();
    }

    private void appendField(StringBuilder builder, String label, String value) {
        String trimmed = Strings.defaultIfBlank(value, "").trim();
        if (!trimmed.isBlank()) {
            appendLine(builder, label + ": " + trimmed);
        }
    }

    private void appendLine(StringBuilder builder, String line) {
        builder.append(line).append('\n');
    }

    private java.util.Optional<Instant> parseTimestamp(String value) {
        String trimmed = Strings.defaultIfBlank(value, "").trim();
        if (trimmed.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(OffsetDateTime.parse(trimmed).toInstant());
        } catch (DateTimeParseException exception) {
            return java.util.Optional.empty();
        }
    }

    private String cqlSpaceLiteral(String spaceKey) {
        return "\"" + spaceKey.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private JsonNode getJson(String instanceUrl, String email, String token, String path, String requestName) {
        HttpRequest request = HttpRequest.newBuilder(confluenceUri(instanceUrl, path))
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
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Confluence rejected the saved credentials");
            }
            if (status == HttpStatus.FORBIDDEN.value()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Confluence does not allow this account to read pages");
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

    private URI confluenceUri(String instanceUrl, String path) {
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

    public record ConfluencePageManifest(
            String id,
            String title,
            String reference,
            String displayName,
            String format,
            long sizeBytes,
            Instant lastModifiedAt
    ) {
    }

    public record ConfluencePageDocument(
            String id,
            String title,
            String reference,
            String displayName,
            String format,
            long sizeBytes,
            Instant lastModifiedAt,
            byte[] readValue
    ) {
    }
}
