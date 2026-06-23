package ai.corporatedroneagent.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gates the MCP transport endpoints on the {@code mcpServerEnabled} setting. The Spring AI beans
 * stay registered; when the setting is off this filter short-circuits every request to the SSE
 * stream and message endpoint with 503, so no client can initialise a session or call a tool.
 * That makes the Settings → Tools toggle take effect at runtime, without a restart.
 */
public class McpServerEnabledFilter extends OncePerRequestFilter {

    private final BooleanSupplier enabled;

    public McpServerEnabledFilter(BooleanSupplier enabled) {
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled.getAsBoolean()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "MCP server is disabled.");
            return;
        }
        chain.doFilter(request, response);
    }
}
