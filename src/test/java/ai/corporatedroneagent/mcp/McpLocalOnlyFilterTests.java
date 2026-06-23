package ai.corporatedroneagent.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class McpLocalOnlyFilterTests {

    private final McpLocalOnlyFilter filter = new McpLocalOnlyFilter();

    @Test
    void allowsLoopbackHostWithoutOrigin() throws Exception {
        assertThat(status("localhost:8080", null)).isEqualTo(200);
        assertThat(status("127.0.0.1:8080", null)).isEqualTo(200);
        assertThat(status("[::1]:8080", null)).isEqualTo(200);
    }

    @Test
    void allowsLoopbackOrigin() throws Exception {
        assertThat(status("localhost:8080", "http://localhost:8080")).isEqualTo(200);
        assertThat(status("127.0.0.1:8080", "http://127.0.0.1:5173")).isEqualTo(200);
    }

    @Test
    void rejectsNonLocalHost() throws Exception {
        assertThat(status("evil.com", null)).isEqualTo(403);
        assertThat(status("evil.com:8080", null)).isEqualTo(403);
    }

    @Test
    void rejectsNonLocalOriginEvenWithLocalHost() throws Exception {
        // The DNS-rebinding case: Host looks local but the browser's Origin betrays the attacker.
        assertThat(status("localhost:8080", "http://evil.com")).isEqualTo(403);
    }

    @Test
    void rejectsMissingOrMalformedHost() throws Exception {
        assertThat(status(null, null)).isEqualTo(403);
        assertThat(status("localhost:8080", "null")).isEqualTo(403);
    }

    private int status(String host, String origin) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp/message");
        if (host != null) {
            request.addHeader("Host", host);
        }
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }
}
