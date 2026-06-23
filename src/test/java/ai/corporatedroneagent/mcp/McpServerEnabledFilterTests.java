package ai.corporatedroneagent.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class McpServerEnabledFilterTests {

    @Test
    void passesThroughWhenEnabled() throws Exception {
        assertThat(status(true)).isEqualTo(200);
    }

    @Test
    void shortCircuitsWith503WhenDisabled() throws Exception {
        assertThat(status(false)).isEqualTo(503);
    }

    private int status(boolean enabled) throws ServletException, IOException {
        McpServerEnabledFilter filter = new McpServerEnabledFilter(() -> enabled);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp/message");
        request.addHeader("Host", "localhost:8080");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }
}
