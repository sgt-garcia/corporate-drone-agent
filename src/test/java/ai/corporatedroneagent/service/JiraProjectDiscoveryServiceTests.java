package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.corporatedroneagent.dto.JiraProjectDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class JiraProjectDiscoveryServiceTests {

    private HttpServer server;
    private JiraProjectDiscoveryService service;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        service = new JiraProjectDiscoveryService(new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void searchesJiraProjectsWithBasicAuthAndQuery() {
        AtomicReference<String> authHeader = new AtomicReference<>();
        AtomicReference<String> queryString = new AtomicReference<>();
        server.createContext("/rest/api/3/project/search", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            queryString.set(exchange.getRequestURI().getQuery());
            respond(exchange, 200, """
                    {
                      "values": [
                        {
                          "id": "10001",
                          "key": "DEV",
                          "name": "Software Development",
                          "insight": { "totalIssueCount": 42 }
                        }
                      ]
                    }
                    """);
        });

        var projects = service.searchProjects(baseUrl(), "me@example.com", "token-1234", "dev ops", 25);

        assertThat(authHeader.get()).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString("me@example.com:token-1234".getBytes(StandardCharsets.UTF_8)));
        assertThat(queryString.get()).contains("maxResults=25", "query=dev+ops");
        assertThat(projects).hasSize(1);
        assertThat(projects.getFirst().id()).isEqualTo("10001");
        assertThat(projects.getFirst().key()).isEqualTo("DEV");
        assertThat(projects.getFirst().name()).isEqualTo("Software Development");
        assertThat(projects.getFirst().issues()).isEqualTo(42);
    }

    @Test
    void parsesArrayProjectResponsesAndSkipsIncompleteProjects() {
        server.createContext("/rest/api/3/project/search", exchange -> respond(exchange, 200, """
                [
                  {
                    "id": "10001",
                    "key": "DEV",
                    "name": "Software Development",
                    "insight": { "totalIssueCount": 42 }
                  },
                  {
                    "id": "10002",
                    "key": "",
                    "name": "No Key"
                  },
                  {
                    "id": "10003",
                    "key": "OPS",
                    "name": "",
                    "insight": { "totalIssueCount": 12 }
                  },
                  {
                    "key": "HLP",
                    "name": "Help Desk",
                    "insight": { "totalIssueCount": -9 }
                  }
                ]
                """));

        var projects = service.searchProjects(baseUrl(), "me@example.com", "token-1234", "", 25);

        assertThat(projects)
                .extracting(JiraProjectDto::key)
                .containsExactly("DEV", "HLP");
        assertThat(projects.get(1).id()).isEqualTo("jira-hlp");
        assertThat(projects.get(1).issues()).isZero();
    }

    @Test
    void browsesEveryProjectPageForABlankQuery() {
        List<String> requestedQueries = new CopyOnWriteArrayList<>();
        server.createContext("/rest/api/3/project/search", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            requestedQueries.add(query);
            if (query.contains("startAt=0")) {
                // A full page (50) must trigger a follow-up fetch for the rest.
                StringBuilder values = new StringBuilder();
                for (int i = 0; i < 50; i++) {
                    if (i > 0) {
                        values.append(",");
                    }
                    values.append("{\"id\":\"")
                            .append(10000 + i)
                            .append("\",\"key\":\"P")
                            .append(i)
                            .append("\",\"name\":\"Project ")
                            .append(i)
                            .append("\"}");
                }
                respond(exchange, 200, "{\"isLast\":false,\"values\":[" + values + "]}");
            } else {
                respond(exchange, 200, "{\"isLast\":true,\"values\":["
                        + "{\"id\":\"20001\",\"key\":\"LAST\",\"name\":\"Last Project\"}]}");
            }
        });

        var projects = service.searchProjects(baseUrl(), "me@example.com", "token-1234", "", 25);

        assertThat(projects).hasSize(51);
        assertThat(projects).extracting(JiraProjectDto::key).contains("P0", "P49", "LAST");
        assertThat(requestedQueries).anyMatch(query -> query.contains("startAt=0"));
        assertThat(requestedQueries).anyMatch(query -> query.contains("startAt=50"));
    }

    @Test
    void searchesJiraServerProjectsWithDetectedV2Api() {
        AtomicReference<String> path = new AtomicReference<>();
        server.createContext("/rest/api/2/project", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            respond(exchange, 200, """
                    [
                      {
                        "id": "10001",
                        "key": "DEV",
                        "name": "Software Development"
                      },
                      {
                        "id": "10002",
                        "key": "OPS",
                        "name": "Operations"
                      }
                    ]
                    """);
        });

        var projects = service.searchProjects(baseUrl(), "me@example.com", "token-1234", "ops", 25, "2");

        assertThat(path.get()).isEqualTo("/rest/api/2/project");
        assertThat(projects)
                .extracting(JiraProjectDto::key)
                .containsExactly("OPS");
    }

    @Test
    void returnsEveryJiraServerProjectForABlankQueryIgnoringTheCap() {
        server.createContext("/rest/api/2/project", exchange -> {
            StringBuilder body = new StringBuilder("[");
            for (int i = 0; i < 30; i++) {
                if (i > 0) {
                    body.append(",");
                }
                body.append("{\"id\":\"")
                        .append(10000 + i)
                        .append("\",\"key\":\"P")
                        .append(i)
                        .append("\",\"name\":\"Project ")
                        .append(i)
                        .append("\"}");
            }
            body.append("]");
            respond(exchange, 200, body.toString());
        });

        // The picker sends a blank query and filters client-side, so the cap must not truncate;
        // P25..P29 would be unreachable in the picker if the 25-limit still applied here.
        var projects = service.searchProjects(baseUrl(), "me@example.com", "token-1234", "", 25, "2");

        assertThat(projects).hasSize(30);
        assertThat(projects).extracting(JiraProjectDto::key).contains("P0", "P25", "P29");
    }

    @Test
    void getsProjectByKey() {
        server.createContext("/rest/api/3/project/DEV", exchange -> respond(exchange, 200, """
                {
                  "id": "10001",
                  "key": "DEV",
                  "name": "Software Development",
                  "insight": { "totalIssueCount": 42 }
                }
                """));

        var project = service.getProject(baseUrl(), "me@example.com", "token-1234", "DEV");

        assertThat(project.id()).isEqualTo("10001");
        assertThat(project.key()).isEqualTo("DEV");
        assertThat(project.checked()).isEqualTo("just now");
    }

    @Test
    void surfacesCredentialRejection() {
        server.createContext("/rest/api/3/project/search", exchange -> respond(exchange, 401, ""));

        assertThatThrownBy(() -> service.searchProjects(baseUrl(), "me@example.com", "wrong", "", 25))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Jira rejected the saved credentials");
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
}
