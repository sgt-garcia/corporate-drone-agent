package ai.corporatedroneagent.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects MCP requests whose Host or Origin is not loopback. Binding to localhost keeps
 * off-machine clients out, but a browser can still be steered to 127.0.0.1 via DNS rebinding —
 * at which point its requests carry the attacker's Host/Origin. The MCP spec calls for validating
 * both on local servers; this is that guard. Registered only for the MCP endpoints.
 */
public class McpLocalOnlyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(McpLocalOnlyFilter.class);
    private static final Set<String> ALLOWED_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String host = request.getHeader("Host");
        if (!isLocal(hostName(host))) {
            reject(request, response, "Host", host);
            return;
        }
        // Origin is set by browsers; non-browser MCP clients omit it, so only validate when present.
        String origin = request.getHeader("Origin");
        if (origin != null && !isLocal(originHost(origin))) {
            reject(request, response, "Origin", origin);
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String header, String value)
            throws IOException {
        log.warn("Rejected MCP request to {} — non-local {} header: {}", request.getRequestURI(), header, value);
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "MCP server accepts local requests only.");
    }

    private static boolean isLocal(String host) {
        return host != null && ALLOWED_HOSTS.contains(host);
    }

    // The bare host from a Host header, lower-cased, without the optional port or IPv6 brackets.
    private static String hostName(String hostHeader) {
        if (hostHeader == null || hostHeader.isBlank()) {
            return null;
        }
        String host = hostHeader.trim();
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            return end > 0 ? host.substring(1, end).toLowerCase() : null;
        }
        int colon = host.indexOf(':');
        return (colon >= 0 ? host.substring(0, colon) : host).toLowerCase();
    }

    // The bare host from an Origin header; returns null for malformed values (e.g. the literal
    // "null" origin sent by sandboxed pages), which then fails the local check.
    private static String originHost(String origin) {
        try {
            String host = new URI(origin).getHost();
            if (host == null) {
                return null;
            }
            host = host.toLowerCase();
            return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
        } catch (URISyntaxException exception) {
            return null;
        }
    }
}
