package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JiraConnectionValidationServiceTests {

    private HttpServer server;
    private JiraConnectionValidationService service;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        service = new JiraConnectionValidationService();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void validatesAgainstCloudMyselfEndpointWithBasicAuth() {
        AtomicReference<String> authHeader = new AtomicReference<>();
        server.createContext("/rest/api/3/myself", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"accountId\":\"abc\"}");
        });

        var result = service.validate(baseUrl(), "me@example.com", "token-1234");

        assertThat(result.valid()).isTrue();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(authHeader.get()).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString("me@example.com:token-1234".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void returnsInvalidWhenJiraRejectsCredentials() {
        server.createContext("/rest/api/3/myself", exchange -> respond(exchange, 401, ""));

        var result = service.validate(baseUrl(), "me@example.com", "wrong-token");

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).isEqualTo("Jira rejected the email or API token.");
        assertThat(result.statusCode()).isEqualTo(401);
    }

    @Test
    void fallsBackToV2MyselfWhenV3IsNotAvailable() {
        AtomicBoolean v2Called = new AtomicBoolean();
        server.createContext("/rest/api/3/myself", exchange -> respond(exchange, 404, ""));
        server.createContext("/rest/api/2/myself", exchange -> {
            v2Called.set(true);
            respond(exchange, 200, "{\"name\":\"me\"}");
        });

        var result = service.validate(baseUrl(), "me@example.com", "token-1234");

        assertThat(result.valid()).isTrue();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(v2Called).isTrue();
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
