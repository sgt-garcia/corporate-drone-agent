package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import ai.corporatedroneagent.dto.JiraProjectDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JiraIssueFetchServiceTests {

    private HttpServer server;
    private JiraIssueFetchService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        objectMapper = new ObjectMapper();
        service = new JiraIssueFetchService(HttpClient.newHttpClient(), objectMapper);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void fetchesIssueManifestWithOnlyBasicFields() {
        AtomicReference<String> authHeader = new AtomicReference<>();
        AtomicReference<String> issueSearchQuery = new AtomicReference<>();
        server.createContext("/rest/api/3/search/jql", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            issueSearchQuery.set(exchange.getRequestURI().getQuery());
            respond(exchange, 200, """
                    {
                      "isLast": true,
                      "issues": [
                        {
                          "id": "10100",
                          "key": "DEV-7",
                          "fields": {
                            "summary": "Add scan pipeline",
                            "updated": "2026-06-09T12:34:56.000+0000"
                          }
                        }
                      ]
                    }
                    """);
        });

        var manifests = service.fetchProjectIssueManifests(
                baseUrl(),
                "me@example.com",
                "token-1234",
                project("10001", "DEV")
        );

        assertThat(authHeader.get()).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString("me@example.com:token-1234".getBytes(StandardCharsets.UTF_8)));
        String decodedQuery = URLDecoder.decode(issueSearchQuery.get(), StandardCharsets.UTF_8);
        assertThat(decodedQuery)
                .contains("project = DEV ORDER BY updated DESC")
                .contains("fields=summary,status,issuetype,updated")
                .contains("maxResults=50");
        assertThat(manifests)
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.id()).isEqualTo("10100");
                    assertThat(issue.key()).isEqualTo("DEV-7");
                    assertThat(issue.reference()).isEqualTo("jira://127.0.0.1:" + server.getAddress().getPort() + "/issue/10100");
                    assertThat(issue.displayName()).isEqualTo("DEV-7 - Add scan pipeline");
                    assertThat(issue.lastModifiedAt()).isEqualTo("2026-06-09T12:34:56Z");
                });
    }

    @Test
    void fetchesOnlyIssueManifestsUpdatedSinceProvidedTimestamp() {
        AtomicReference<String> issueSearchQuery = new AtomicReference<>();
        server.createContext("/rest/api/3/search/jql", exchange -> {
            issueSearchQuery.set(exchange.getRequestURI().getQuery());
            respond(exchange, 200, """
                    {
                      "isLast": true,
                      "issues": []
                    }
                    """);
        });

        var manifests = service.fetchProjectIssueManifests(
                baseUrl(),
                "me@example.com",
                "token-1234",
                project("10001", "DEV"),
                Instant.parse("2026-06-09T12:34:56Z")
        );

        assertThat(manifests).isEmpty();
        assertThat(URLDecoder.decode(issueSearchQuery.get(), StandardCharsets.UTF_8))
                .contains("updated >= \"2026/06/08 12:34\"")
                .contains("ORDER BY updated DESC");
    }

    @Test
    void readsNativeIssuePayloadWithPagedCommentsAndConvertsToMarkdown() throws IOException {
        server.createContext("/rest/api/3/issue/DEV-7", exchange -> respond(exchange, 200, """
                {
                  "id": "10100",
                  "key": "DEV-7",
                  "fields": {
                    "summary": "Add scan pipeline",
                    "description": {
                      "type": "doc",
                      "content": [
                        {
                          "type": "paragraph",
                          "content": [
                            { "type": "text", "text": "Index Jira issues" }
                          ]
                        }
                      ]
                    },
                    "status": {
                      "name": "In Progress",
                      "statusCategory": { "name": "In Progress" }
                    },
                    "issuetype": { "name": "Task" },
                    "priority": { "name": "High" },
                    "assignee": { "displayName": "Ada Lovelace", "accountId": "ada" },
                    "reporter": { "displayName": "Grace Hopper", "accountId": "grace" },
                    "creator": { "displayName": "Margaret Hamilton" },
                    "labels": [ "knowledge" ],
                    "components": [ { "name": "Backend" } ],
                    "fixVersions": [ { "name": "1.2.0" } ],
                    "versions": [ { "name": "1.1.0" } ],
                    "project": { "key": "DEV", "name": "Software Development" },
                    "created": "2026-06-01T10:00:00.000+0000",
                    "updated": "2026-06-09T12:34:56.000+0000"
                  }
                }
                """));
        AtomicInteger commentCalls = new AtomicInteger();
        server.createContext("/rest/api/3/issue/DEV-7/comment", exchange -> {
            int call = commentCalls.incrementAndGet();
            int startAt = Integer.parseInt(queryParameter(exchange.getRequestURI().getQuery(), "startAt"));
            respond(exchange, 200, commentsResponse(startAt, call == 1 ? 75 : 75));
        });
        JiraIssueFetchService.JiraIssueManifest manifest = manifest("10100", "DEV-7", "Add scan pipeline");

        JiraIssueFetchService.JiraIssueDocument document = service.fetchIssueDocument(
                baseUrl(),
                "me@example.com",
                "token-1234",
                "3",
                manifest
        );

        JsonNode payload = objectMapper.readTree(document.readValue());
        assertThat(payload.path("issue").path("key").asText()).isEqualTo("DEV-7");
        assertThat(payload.path("comments")).hasSize(75);
        assertThat(commentCalls).hasValue(2);

        assertThat(service.toMarkdown(document.readValue()))
                .contains("# DEV-7 - Add scan pipeline")
                .contains("Issue key: DEV-7")
                .contains("Status: In Progress")
                .contains("Project: DEV - Software Development")
                .contains("Description")
                .contains("Index Jira issues")
                .contains("Reviewer 0")
                .contains("Comment 74");
    }

    @Test
    void fetchesEveryManifestAcrossAllJiraPages() {
        AtomicInteger issueSearchCalls = new AtomicInteger();
        server.createContext("/rest/api/3/search/jql", exchange -> {
            issueSearchCalls.incrementAndGet();
            String token = queryParameter(exchange.getRequestURI().getQuery(), "nextPageToken");
            if (token.isBlank()) {
                respond(exchange, 200, issueSearchResponse(1, 50, false, "page-2"));
            } else if ("page-2".equals(token)) {
                respond(exchange, 200, issueSearchResponse(51, 20, true, ""));
            } else {
                respond(exchange, 400, "{}");
            }
        });

        var manifests = service.fetchProjectIssueManifests(
                baseUrl(),
                "me@example.com",
                "token-1234",
                project("10001", "DEV")
        );

        assertThat(issueSearchCalls).hasValue(2);
        assertThat(manifests).hasSize(70);
        assertThat(manifests.getFirst().key()).isEqualTo("DEV-1");
        assertThat(manifests.getLast().key()).isEqualTo("DEV-70");
    }

    @Test
    void fetchesJiraServerManifestsWithDetectedV2Api() {
        AtomicReference<String> searchPath = new AtomicReference<>();
        AtomicReference<String> searchQuery = new AtomicReference<>();
        server.createContext("/rest/api/2/search", exchange -> {
            searchPath.set(exchange.getRequestURI().getPath());
            searchQuery.set(exchange.getRequestURI().getQuery());
            respond(exchange, 200, """
                    {
                      "startAt": 0,
                      "maxResults": 50,
                      "total": 1,
                      "issues": [
                        {
                          "id": "10100",
                          "key": "DEV-7",
                          "fields": {
                            "summary": "Server issue",
                            "updated": "2026-06-09T12:34:56.000+0000"
                          }
                        }
                      ]
                    }
                    """);
        });

        var manifests = service.fetchProjectIssueManifests(
                baseUrl(),
                "me@example.com",
                "token-1234",
                project("10001", "DEV"),
                Instant.parse("2026-06-09T12:34:56Z"),
                "2"
        );

        assertThat(searchPath.get()).isEqualTo("/rest/api/2/search");
        assertThat(URLDecoder.decode(searchQuery.get(), StandardCharsets.UTF_8))
                .contains("startAt=0")
                .contains("updated >= \"2026/06/08 12:34\"");
        assertThat(manifests)
                .singleElement()
                .satisfies(issue -> assertThat(issue.displayName()).isEqualTo("DEV-7 - Server issue"));
    }

    private JiraProjectDto project(String id, String key) {
        JiraProjectDto project = new JiraProjectDto();
        project.setId(id);
        project.setKey(key);
        project.setName("Software Development");
        return project;
    }

    private JiraIssueFetchService.JiraIssueManifest manifest(String id, String key, String summary) {
        return new JiraIssueFetchService.JiraIssueManifest(
                id,
                key,
                "jira://127.0.0.1:" + server.getAddress().getPort() + "/issue/" + id,
                key + " - " + summary,
                "jira-issue",
                0,
                Instant.parse("2026-06-09T12:34:56Z")
        );
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String issueSearchResponse(int startIssue, int count, boolean isLast, String nextPageToken) {
        StringBuilder issues = new StringBuilder();
        for (int offset = 0; offset < count; offset++) {
            int issueNumber = startIssue + offset;
            if (!issues.isEmpty()) {
                issues.append(',');
            }
            issues.append("""
                    {
                      "id": "%d",
                      "key": "DEV-%d",
                      "fields": {
                        "summary": "Issue %d",
                        "updated": "2026-06-09T12:34:56.000+0000"
                      }
                    }
                    """.formatted(10000 + issueNumber, issueNumber, issueNumber));
        }
        String tokenField = nextPageToken.isBlank()
                ? ""
                : ", \"nextPageToken\": \"" + nextPageToken + "\"";
        return """
                {
                  "isLast": %s%s,
                  "issues": [%s]
                }
                """.formatted(isLast, tokenField, issues);
    }

    private String commentsResponse(int startAt, int total) {
        int count = Math.min(50, total - startAt);
        StringBuilder comments = new StringBuilder();
        for (int offset = 0; offset < count; offset++) {
            int commentNumber = startAt + offset;
            if (!comments.isEmpty()) {
                comments.append(',');
            }
            comments.append("""
                    {
                      "author": { "displayName": "Reviewer %d" },
                      "created": "2026-06-09T13:00:00.000+0000",
                      "updated": "2026-06-09T13:01:00.000+0000",
                      "body": {
                        "type": "doc",
                        "content": [
                          {
                            "type": "paragraph",
                            "content": [
                              { "type": "text", "text": "Comment %d" }
                            ]
                          }
                        ]
                      }
                    }
                    """.formatted(commentNumber, commentNumber));
        }
        return """
                {
                  "startAt": %d,
                  "maxResults": 50,
                  "total": %d,
                  "comments": [%s]
                }
                """.formatted(startAt, total, comments);
    }

    private String queryParameter(String query, String name) {
        if (query == null || query.isBlank()) {
            return "";
        }
        for (String part : query.split("&")) {
            int equals = part.indexOf('=');
            String key = equals < 0 ? part : part.substring(0, equals);
            if (key.equals(name)) {
                String value = equals < 0 ? "" : part.substring(equals + 1);
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
            }
        }
        return "";
    }
}
