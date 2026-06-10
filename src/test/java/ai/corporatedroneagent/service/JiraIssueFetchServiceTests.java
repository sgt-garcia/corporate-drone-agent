package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import ai.corporatedroneagent.dto.JiraProjectDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JiraIssueFetchServiceTests {

    private HttpServer server;
    private JiraIssueFetchService service;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        service = new JiraIssueFetchService(HttpClient.newHttpClient(), new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void fetchesProjectIssuesAndConvertsFieldsAndCommentsToText() {
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
                            "status": { "name": "In Progress" },
                            "issuetype": { "name": "Task" },
                            "priority": { "name": "High" },
                            "assignee": { "displayName": "Ada Lovelace" },
                            "reporter": { "displayName": "Grace Hopper" },
                            "labels": [ "knowledge" ],
                            "components": [ { "name": "Backend" } ],
                            "fixVersions": [ { "name": "1.2.0" } ],
                            "created": "2026-06-01T10:00:00.000+0000",
                            "updated": "2026-06-09T12:34:56.000+0000"
                          }
                        }
                      ]
                    }
                    """);
        });
        server.createContext("/rest/api/3/issue/DEV-7/comment", exchange -> respond(exchange, 200, """
                {
                  "startAt": 0,
                  "maxResults": 50,
                  "total": 1,
                  "comments": [
                    {
                      "author": { "displayName": "Linus Torvalds" },
                      "created": "2026-06-09T13:00:00.000+0000",
                      "body": {
                        "type": "doc",
                        "content": [
                          {
                            "type": "paragraph",
                            "content": [
                              { "type": "text", "text": "Please include comments." }
                            ]
                          }
                        ]
                      }
                    }
                  ]
                }
                """));

        var issues = service.fetchProjectIssues(
                baseUrl(),
                "me@example.com",
                "token-1234",
                project("10001", "DEV")
        );

        assertThat(authHeader.get()).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString("me@example.com:token-1234".getBytes(StandardCharsets.UTF_8)));
        assertThat(issueSearchQuery.get())
                .contains("jql=project+=+DEV+ORDER+BY+updated+DESC")
                .contains("maxResults=50")
                .contains("fields=summary");
        assertThat(issues)
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.id()).isEqualTo("10100");
                    assertThat(issue.key()).isEqualTo("DEV-7");
                    assertThat(issue.reference()).isEqualTo("jira://127.0.0.1:" + server.getAddress().getPort() + "/issue/10100");
                    assertThat(issue.displayName()).isEqualTo("DEV-7 - Add scan pipeline");
                    assertThat(issue.lastModifiedAt()).isEqualTo("2026-06-09T12:34:56Z");
                    assertThat(issue.text())
                            .contains("Summary: Add scan pipeline")
                            .contains("Status: In Progress")
                            .contains("Description:\nIndex Jira issues")
                            .contains("Linus Torvalds")
                            .contains("Please include comments.");
                });
    }

    @Test
    void fetchesEveryIssueAcrossAllJiraPages() {
        AtomicInteger issueSearchCalls = new AtomicInteger();
        List<String> pageTokens = new ArrayList<>();
        server.createContext("/rest/api/3/search/jql", exchange -> {
            issueSearchCalls.incrementAndGet();
            String query = exchange.getRequestURI().getQuery();
            pageTokens.add(query == null ? "" : query);
            String token = queryParameter(query, "nextPageToken");
            if (token.isBlank()) {
                respond(exchange, 200, issueSearchResponse(1, 50, false, "page-2"));
            } else if ("page-2".equals(token)) {
                respond(exchange, 200, issueSearchResponse(51, 50, false, "page-3"));
            } else if ("page-3".equals(token)) {
                respond(exchange, 200, issueSearchResponse(101, 20, true, ""));
            } else {
                respond(exchange, 400, "{}");
            }
        });
        server.createContext("/rest/api/3/issue/", exchange -> respond(exchange, 200, """
                {
                  "startAt": 0,
                  "maxResults": 50,
                  "total": 0,
                  "comments": []
                }
                """));

        var issues = service.fetchProjectIssues(
                baseUrl(),
                "me@example.com",
                "token-1234",
                project("10001", "DEV")
        );

        assertThat(issueSearchCalls).hasValue(3);
        assertThat(pageTokens.get(0)).doesNotContain("nextPageToken");
        assertThat(pageTokens.get(1)).contains("nextPageToken=page-2");
        assertThat(pageTokens.get(2)).contains("nextPageToken=page-3");
        assertThat(issues).hasSize(120);
        assertThat(issues.getFirst().key()).isEqualTo("DEV-1");
        assertThat(issues.get(99).key()).isEqualTo("DEV-100");
        assertThat(issues.getLast().key()).isEqualTo("DEV-120");
    }

    private JiraProjectDto project(String id, String key) {
        JiraProjectDto project = new JiraProjectDto();
        project.setId(id);
        project.setKey(key);
        project.setName("Software Development");
        return project;
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

    private String queryParameter(String query, String name) {
        if (query == null || query.isBlank()) {
            return "";
        }
        for (String part : query.split("&")) {
            int equals = part.indexOf('=');
            String key = equals < 0 ? part : part.substring(0, equals);
            if (key.equals(name)) {
                return equals < 0 ? "" : part.substring(equals + 1);
            }
        }
        return "";
    }
}
