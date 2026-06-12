package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.JiraProjectDto;
import ai.corporatedroneagent.model.knowledge.JiraKnowledgeReferences;
import ai.corporatedroneagent.util.Strings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JiraIssueFetchService {

    static final int ISSUE_PAGE_SIZE = 50;
    static final int COMMENT_PAGE_SIZE = 50;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration DELTA_QUERY_TIMEZONE_SAFETY_OVERLAP = Duration.ofHours(24);
    private static final DateTimeFormatter JIRA_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private static final DateTimeFormatter JQL_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private static final String MANIFEST_FIELDS = String.join(
            ",",
            "summary",
            "status",
            "issuetype",
            "updated"
    );

    private static final String READ_FIELDS = String.join(
            ",",
            "summary",
            "description",
            "status",
            "issuetype",
            "priority",
            "assignee",
            "reporter",
            "creator",
            "labels",
            "components",
            "fixVersions",
            "versions",
            "project",
            "parent",
            "resolution",
            "created",
            "updated",
            "resolutiondate",
            "duedate"
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public JiraIssueFetchService(ObjectMapper objectMapper) {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(REQUEST_TIMEOUT)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build(),
                objectMapper
        );
    }

    JiraIssueFetchService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public List<JiraIssueManifest> fetchProjectIssueManifests(
            String instanceUrl,
            String email,
            String token,
            JiraProjectDto project
    ) {
        return fetchProjectIssueManifests(instanceUrl, email, token, project, null);
    }

    public List<JiraIssueManifest> fetchProjectIssueManifests(
            String instanceUrl,
            String email,
            String token,
            JiraProjectDto project,
            Instant updatedSince
    ) {
        return fetchProjectIssueManifests(instanceUrl, email, token, project, updatedSince, "3");
    }

    public List<JiraIssueManifest> fetchProjectIssueManifests(
            String instanceUrl,
            String email,
            String token,
            JiraProjectDto project,
            Instant updatedSince,
            String apiVersion
    ) {
        String projectKey = Strings.defaultIfBlank(project.getKey(), "").trim();
        if (projectKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Jira project key is required");
        }

        List<JiraIssueManifest> manifests = new ArrayList<>();
        String normalizedApiVersion = normalizeApiVersion(apiVersion);
        if ("2".equals(normalizedApiVersion)) {
            fetchProjectIssueManifestsV2(instanceUrl, email, token, projectKey, updatedSince, manifests);
        } else {
            fetchProjectIssueManifestsV3(instanceUrl, email, token, projectKey, updatedSince, manifests);
        }
        return manifests;
    }

    public JiraIssueDocument fetchIssueDocument(
            String instanceUrl,
            String email,
            String token,
            String apiVersion,
            JiraIssueManifest manifest
    ) {
        String normalizedApiVersion = normalizeApiVersion(apiVersion);
        JsonNode issue = getJson(
                instanceUrl,
                email,
                token,
                issuePath(normalizedApiVersion, manifest.key()),
                "Jira issue"
        );
        ArrayNode comments = objectMapper.createArrayNode();
        fetchComments(instanceUrl, email, token, normalizedApiVersion, manifest.key()).forEach(comments::add);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("source", "jira");
        payload.put("apiVersion", normalizedApiVersion);
        payload.put("fetchedAt", Instant.now().toString());
        payload.set("issue", issue);
        payload.set("comments", comments);

        try {
            byte[] value = objectMapper.writeValueAsBytes(payload);
            JsonNode fields = issue.path("fields");
            String id = Strings.defaultIfBlank(issue.path("id").asText(""), manifest.id()).trim();
            String key = Strings.defaultIfBlank(issue.path("key").asText(""), manifest.key()).trim();
            return new JiraIssueDocument(
                    id,
                    key,
                    JiraKnowledgeReferences.issueResourceReference(instanceUrl, id),
                    displayName(key, fields.path("summary").asText("")),
                    "jira-issue",
                    value.length,
                    parseJiraTimestamp(fields.path("updated").asText("")).orElse(manifest.lastModifiedAt()),
                    value
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not serialize Jira issue");
        }
    }

    public String toMarkdown(byte[] readValue) {
        try {
            JsonNode payload = objectMapper.readTree(readValue == null ? new byte[0] : readValue);
            JsonNode issue = payload.path("issue");
            String key = Strings.defaultIfBlank(issue.path("key").asText(""), "").trim();
            return issueText(key, issue.path("fields"), payload.path("comments"));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not parse Jira issue payload");
        }
    }

    private void fetchProjectIssueManifestsV3(
            String instanceUrl,
            String email,
            String token,
            String projectKey,
            Instant updatedSince,
            List<JiraIssueManifest> manifests
    ) {
        String nextPageToken = "";
        while (true) {
            JsonNode response = getJson(
                    instanceUrl,
                    email,
                    token,
                    issueSearchPathV3(projectKey, updatedSince, ISSUE_PAGE_SIZE, nextPageToken),
                    "Jira issue search"
            );
            JsonNode issues = response.path("issues");
            if (!issues.isArray() || issues.isEmpty()) {
                break;
            }
            for (JsonNode issue : issues) {
                manifests.add(toIssueManifest(instanceUrl, issue));
            }
            if (response.path("isLast").asBoolean(false)) {
                break;
            }
            nextPageToken = Strings.defaultIfBlank(response.path("nextPageToken").asText(""), "").trim();
            if (nextPageToken.isBlank()) {
                break;
            }
        }
    }

    private void fetchProjectIssueManifestsV2(
            String instanceUrl,
            String email,
            String token,
            String projectKey,
            Instant updatedSince,
            List<JiraIssueManifest> manifests
    ) {
        int startAt = 0;
        while (true) {
            JsonNode response = getJson(
                    instanceUrl,
                    email,
                    token,
                    issueSearchPathV2(projectKey, updatedSince, ISSUE_PAGE_SIZE, startAt),
                    "Jira issue search"
            );
            JsonNode issues = response.path("issues");
            if (!issues.isArray() || issues.isEmpty()) {
                break;
            }
            for (JsonNode issue : issues) {
                manifests.add(toIssueManifest(instanceUrl, issue));
            }
            int responseStartAt = response.path("startAt").asInt(startAt);
            startAt = responseStartAt + issues.size();
            int total = response.path("total").asInt(startAt);
            if (startAt >= total) {
                break;
            }
        }
    }

    private JiraIssueManifest toIssueManifest(String instanceUrl, JsonNode issue) {
        String id = Strings.defaultIfBlank(issue.path("id").asText(""), "").trim();
        String key = Strings.defaultIfBlank(issue.path("key").asText(""), "").trim();
        if (id.isBlank()) {
            id = key;
        }
        if (id.isBlank() || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Jira issue search returned an invalid issue");
        }

        JsonNode fields = issue.path("fields");
        String summary = fields.path("summary").asText("");
        return new JiraIssueManifest(
                id,
                key,
                JiraKnowledgeReferences.issueResourceReference(instanceUrl, id),
                displayName(key, summary),
                "jira-issue",
                manifestSizeBytes(key, summary),
                parseJiraTimestamp(fields.path("updated").asText("")).orElse(null)
        );
    }

    private List<JsonNode> fetchComments(
            String instanceUrl,
            String email,
            String token,
            String apiVersion,
            String issueKey
    ) {
        List<JsonNode> comments = new ArrayList<>();
        int startAt = 0;
        String normalizedApiVersion = normalizeApiVersion(apiVersion);
        while (true) {
            JsonNode response = getJson(
                    instanceUrl,
                    email,
                    token,
                    "/rest/api/" + normalizedApiVersion + "/issue/" + urlEncodePathSegment(issueKey)
                            + "/comment?startAt=" + startAt
                            + "&maxResults=" + COMMENT_PAGE_SIZE,
                    "Jira issue comments"
            );
            JsonNode values = response.path("comments");
            if (!values.isArray() || values.isEmpty()) {
                break;
            }
            values.forEach(comments::add);
            startAt = response.path("startAt").asInt(startAt) + values.size();
            int total = response.path("total").asInt(startAt);
            if (startAt >= total) {
                break;
            }
        }
        return comments;
    }

    private String issueText(String key, JsonNode fields, JsonNode comments) {
        StringBuilder text = new StringBuilder();
        appendLine(text, "# " + displayName(key, fields.path("summary").asText("")));
        appendField(text, "Issue key", key);
        appendField(text, "Summary", fields.path("summary").asText(""));
        appendField(text, "Project", projectName(fields.path("project")));
        appendField(text, "Parent", parentName(fields.path("parent")));
        appendField(text, "Type", fields.path("issuetype").path("name").asText(""));
        appendField(text, "Status", fields.path("status").path("name").asText(""));
        appendField(text, "Status category", fields.path("status").path("statusCategory").path("name").asText(""));
        appendField(text, "Priority", fields.path("priority").path("name").asText(""));
        appendField(text, "Resolution", fields.path("resolution").path("name").asText(""));
        appendField(text, "Assignee", displayName(fields.path("assignee")));
        appendField(text, "Reporter", displayName(fields.path("reporter")));
        appendField(text, "Creator", displayName(fields.path("creator")));
        appendField(text, "Labels", names(fields.path("labels"), ""));
        appendField(text, "Components", names(fields.path("components"), "name"));
        appendField(text, "Fix versions", names(fields.path("fixVersions"), "name"));
        appendField(text, "Affected versions", names(fields.path("versions"), "name"));
        appendField(text, "Created", fields.path("created").asText(""));
        appendField(text, "Updated", fields.path("updated").asText(""));
        appendField(text, "Resolved", fields.path("resolutiondate").asText(""));
        appendField(text, "Due", fields.path("duedate").asText(""));

        String description = extractDocumentText(fields.path("description"));
        if (!description.isBlank()) {
            appendLine(text, "");
            appendLine(text, "## Description");
            appendLine(text, "");
            appendLine(text, description);
        }

        if (comments.isArray() && !comments.isEmpty()) {
            appendLine(text, "");
            appendLine(text, "## Comments");
            for (JsonNode comment : comments) {
                appendLine(text, "");
                String heading = "### " + Strings.defaultIfBlank(displayName(comment.path("author")), "Unknown author");
                String created = Strings.defaultIfBlank(comment.path("created").asText(""), "").trim();
                if (!created.isBlank()) {
                    heading += " at " + created;
                }
                appendLine(text, heading);
                appendField(text, "Updated", comment.path("updated").asText(""));
                appendField(text, "Updated by", displayName(comment.path("updateAuthor")));
                appendField(text, "Visibility", visibility(comment.path("visibility")));
                String body = extractDocumentText(comment.path("body"));
                if (!body.isBlank()) {
                    appendLine(text, body);
                }
            }
        }
        return text.toString().strip();
    }

    private String issuePath(String apiVersion, String issueKey) {
        return "/rest/api/" + apiVersion + "/issue/" + urlEncodePathSegment(issueKey)
                + "?fields=" + urlEncode(READ_FIELDS);
    }

    private long manifestSizeBytes(String key, String summary) {
        return (key + "\n" + Strings.defaultIfBlank(summary, "")).getBytes(StandardCharsets.UTF_8).length;
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

    private String displayName(String key, String summary) {
        String trimmedSummary = Strings.defaultIfBlank(summary, "").trim();
        return trimmedSummary.isBlank() ? key : key + " - " + trimmedSummary;
    }

    private String displayName(JsonNode user) {
        if (user == null || user.isMissingNode() || user.isNull()) {
            return "";
        }
        String displayName = Strings.defaultIfBlank(user.path("displayName").asText(""), "").trim();
        if (!displayName.isBlank()) {
            return displayName;
        }
        return Strings.defaultIfBlank(user.path("emailAddress").asText(""), "").trim();
    }

    private String projectName(JsonNode project) {
        if (project == null || project.isMissingNode() || project.isNull()) {
            return "";
        }
        String key = Strings.defaultIfBlank(project.path("key").asText(""), "").trim();
        String name = Strings.defaultIfBlank(project.path("name").asText(""), "").trim();
        if (key.isBlank()) {
            return name;
        }
        return name.isBlank() ? key : key + " - " + name;
    }

    private String parentName(JsonNode parent) {
        if (parent == null || parent.isMissingNode() || parent.isNull()) {
            return "";
        }
        String key = Strings.defaultIfBlank(parent.path("key").asText(""), "").trim();
        String summary = parent.path("fields").path("summary").asText("");
        return displayName(key, summary);
    }

    private String visibility(JsonNode visibility) {
        if (visibility == null || visibility.isMissingNode() || visibility.isNull()) {
            return "";
        }
        String type = Strings.defaultIfBlank(visibility.path("type").asText(""), "").trim();
        String value = Strings.defaultIfBlank(visibility.path("value").asText(""), "").trim();
        if (type.isBlank()) {
            return value;
        }
        return value.isBlank() ? type : type + ": " + value;
    }

    private String names(JsonNode values, String fieldName) {
        if (!values.isArray()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (JsonNode value : values) {
            String name = fieldName.isBlank() ? value.asText("") : value.path(fieldName).asText("");
            name = Strings.defaultIfBlank(name, "").trim();
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return String.join(", ", names);
    }

    private String extractDocumentText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText("");
        }
        StringBuilder builder = new StringBuilder();
        appendDocumentText(node, builder);
        return builder.toString().replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private void appendDocumentText(JsonNode node, StringBuilder builder) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        String type = node.path("type").asText("");
        if ("text".equals(type)) {
            builder.append(node.path("text").asText(""));
        } else if ("hardBreak".equals(type)) {
            builder.append('\n');
        } else if ("mention".equals(type)) {
            builder.append(node.path("attrs").path("text").asText(""));
        } else if ("inlineCard".equals(type) || "blockCard".equals(type)) {
            builder.append(node.path("attrs").path("url").asText(""));
        }

        JsonNode content = node.path("content");
        if (content.isArray()) {
            for (JsonNode child : content) {
                appendDocumentText(child, builder);
            }
        }
        if (List.of("paragraph", "heading", "blockquote", "codeBlock", "listItem").contains(type)) {
            builder.append('\n');
        }
    }

    private java.util.Optional<Instant> parseJiraTimestamp(String value) {
        String trimmed = Strings.defaultIfBlank(value, "").trim();
        if (trimmed.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(OffsetDateTime.parse(trimmed, JIRA_TIMESTAMP).toInstant());
        } catch (DateTimeParseException ignored) {
            try {
                return java.util.Optional.of(OffsetDateTime.parse(trimmed).toInstant());
            } catch (DateTimeParseException exception) {
                return java.util.Optional.empty();
            }
        }
    }

    private String issueSearchPathV3(String projectKey, Instant updatedSince, int maxResults, String nextPageToken) {
        String jql = "project = " + jqlProjectLiteral(projectKey);
        if (updatedSince != null) {
            Instant safeUpdatedSince = updatedSince.minus(DELTA_QUERY_TIMEZONE_SAFETY_OVERLAP);
            jql += " AND updated >= \"" + JQL_TIMESTAMP.format(safeUpdatedSince.atOffset(ZoneOffset.UTC)) + "\"";
        }
        jql += " ORDER BY updated DESC";
        String path = "/rest/api/3/search/jql?jql=" + urlEncode(jql)
                + "&maxResults=" + maxResults
                + "&fields=" + urlEncode(MANIFEST_FIELDS)
                + "&failFast=false";
        if (!Strings.defaultIfBlank(nextPageToken, "").isBlank()) {
            path += "&nextPageToken=" + urlEncode(nextPageToken);
        }
        return path;
    }

    private String issueSearchPathV2(String projectKey, Instant updatedSince, int maxResults, int startAt) {
        String jql = "project = " + jqlProjectLiteral(projectKey);
        if (updatedSince != null) {
            Instant safeUpdatedSince = updatedSince.minus(DELTA_QUERY_TIMEZONE_SAFETY_OVERLAP);
            jql += " AND updated >= \"" + JQL_TIMESTAMP.format(safeUpdatedSince.atOffset(ZoneOffset.UTC)) + "\"";
        }
        jql += " ORDER BY updated DESC";
        return "/rest/api/2/search?jql=" + urlEncode(jql)
                + "&startAt=" + Math.max(0, startAt)
                + "&maxResults=" + maxResults
                + "&fields=" + urlEncode(MANIFEST_FIELDS);
    }

    private String jqlProjectLiteral(String projectKey) {
        if (projectKey.matches("[A-Z][A-Z0-9_]+")) {
            return projectKey.toUpperCase(Locale.ROOT);
        }
        return "\"" + projectKey.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
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
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Jira does not allow this account to read issues");
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

    private String normalizeApiVersion(String apiVersion) {
        return "2".equals(Strings.defaultIfBlank(apiVersion, "").trim()) ? "2" : "3";
    }

    public record JiraIssueManifest(
            String id,
            String key,
            String reference,
            String displayName,
            String format,
            long sizeBytes,
            Instant lastModifiedAt
    ) {
    }

    public record JiraIssueDocument(
            String id,
            String key,
            String reference,
            String displayName,
            String format,
            long sizeBytes,
            Instant lastModifiedAt,
            byte[] readValue
    ) {
    }
}
