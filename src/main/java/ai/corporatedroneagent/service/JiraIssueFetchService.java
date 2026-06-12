package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.JiraProjectDto;
import ai.corporatedroneagent.model.knowledge.JiraKnowledgeReferences;
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
    private static final String ISSUE_FIELDS = String.join(
            ",",
            "summary",
            "description",
            "status",
            "issuetype",
            "priority",
            "assignee",
            "reporter",
            "labels",
            "components",
            "fixVersions",
            "created",
            "updated"
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

    public List<JiraIssueDocument> fetchProjectIssues(
            String instanceUrl,
            String email,
            String token,
            JiraProjectDto project
    ) {
        return fetchProjectIssues(instanceUrl, email, token, project, null);
    }

    public List<JiraIssueDocument> fetchProjectIssues(
            String instanceUrl,
            String email,
            String token,
            JiraProjectDto project,
            Instant updatedSince
    ) {
        return fetchProjectIssues(instanceUrl, email, token, project, updatedSince, "3");
    }

    public List<JiraIssueDocument> fetchProjectIssues(
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

        List<JiraIssueDocument> documents = new ArrayList<>();
        String normalizedApiVersion = normalizeApiVersion(apiVersion);
        if ("2".equals(normalizedApiVersion)) {
            fetchProjectIssuesV2(instanceUrl, email, token, projectKey, updatedSince, documents);
        } else {
            fetchProjectIssuesV3(instanceUrl, email, token, projectKey, updatedSince, documents);
        }
        return documents;
    }

    private void fetchProjectIssuesV3(
            String instanceUrl,
            String email,
            String token,
            String projectKey,
            Instant updatedSince,
            List<JiraIssueDocument> documents
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
                documents.add(toIssueDocument(instanceUrl, email, token, "3", issue));
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

    private void fetchProjectIssuesV2(
            String instanceUrl,
            String email,
            String token,
            String projectKey,
            Instant updatedSince,
            List<JiraIssueDocument> documents
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
                documents.add(toIssueDocument(instanceUrl, email, token, "2", issue));
            }
            int responseStartAt = response.path("startAt").asInt(startAt);
            startAt = responseStartAt + issues.size();
            int total = response.path("total").asInt(startAt);
            if (startAt >= total) {
                break;
            }
        }
    }

    private JiraIssueDocument toIssueDocument(
            String instanceUrl,
            String email,
            String token,
            String apiVersion,
            JsonNode issue
    ) {
        String id = Strings.defaultIfBlank(issue.path("id").asText(""), "").trim();
        String key = Strings.defaultIfBlank(issue.path("key").asText(""), "").trim();
        if (id.isBlank()) {
            id = key;
        }
        if (id.isBlank() || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Jira issue search returned an invalid issue");
        }

        JsonNode fields = issue.path("fields");
        List<JiraIssueComment> comments = fetchComments(instanceUrl, email, token, apiVersion, key);
        String text = issueText(key, fields, comments);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new JiraIssueDocument(
                id,
                key,
                JiraKnowledgeReferences.issueResourceReference(instanceUrl, id),
                displayName(key, fields.path("summary").asText("")),
                "jira-issue",
                bytes.length,
                parseJiraTimestamp(fields.path("updated").asText("")).orElse(null),
                text
        );
    }

    private List<JiraIssueComment> fetchComments(
            String instanceUrl,
            String email,
            String token,
            String apiVersion,
            String issueKey
    ) {
        List<JiraIssueComment> comments = new ArrayList<>();
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
            for (JsonNode comment : values) {
                comments.add(new JiraIssueComment(
                        displayName(comment.path("author")),
                        comment.path("created").asText(""),
                        extractDocumentText(comment.path("body"))
                ));
            }
            startAt = response.path("startAt").asInt(startAt) + values.size();
            int total = response.path("total").asInt(startAt);
            if (startAt >= total) {
                break;
            }
        }
        return comments;
    }

    private String issueText(String key, JsonNode fields, List<JiraIssueComment> comments) {
        StringBuilder text = new StringBuilder();
        appendLine(text, "Jira issue: " + key);
        appendField(text, "Summary", fields.path("summary").asText(""));
        appendField(text, "Type", fields.path("issuetype").path("name").asText(""));
        appendField(text, "Status", fields.path("status").path("name").asText(""));
        appendField(text, "Priority", fields.path("priority").path("name").asText(""));
        appendField(text, "Assignee", displayName(fields.path("assignee")));
        appendField(text, "Reporter", displayName(fields.path("reporter")));
        appendField(text, "Labels", names(fields.path("labels"), ""));
        appendField(text, "Components", names(fields.path("components"), "name"));
        appendField(text, "Fix versions", names(fields.path("fixVersions"), "name"));
        appendField(text, "Created", fields.path("created").asText(""));
        appendField(text, "Updated", fields.path("updated").asText(""));

        String description = extractDocumentText(fields.path("description"));
        if (!description.isBlank()) {
            appendLine(text, "");
            appendLine(text, "Description:");
            appendLine(text, description);
        }

        if (!comments.isEmpty()) {
            appendLine(text, "");
            appendLine(text, "Comments:");
            for (JiraIssueComment comment : comments) {
                String heading = "- " + Strings.defaultIfBlank(comment.author(), "Unknown author");
                if (!comment.created().isBlank()) {
                    heading += " at " + comment.created();
                }
                appendLine(text, heading + ":");
                appendLine(text, comment.text());
            }
        }
        return text.toString().strip();
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
                + "&fields=" + urlEncode(ISSUE_FIELDS)
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
                + "&fields=" + urlEncode(ISSUE_FIELDS);
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

    private record JiraIssueComment(String author, String created, String text) {
    }

    public record JiraIssueDocument(
            String id,
            String key,
            String reference,
            String displayName,
            String format,
            long sizeBytes,
            Instant lastModifiedAt,
            String text
    ) {
    }
}
