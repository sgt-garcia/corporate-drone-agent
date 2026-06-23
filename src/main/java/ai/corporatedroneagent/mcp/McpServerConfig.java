package ai.corporatedroneagent.mcp;

import ai.corporatedroneagent.service.KnowledgeSearchService;
import ai.corporatedroneagent.tools.KnowledgeSearchTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * POC: exposes the on-demand knowledge search tool over MCP so external clients (e.g. Claude)
 * can query the user's connected knowledge sources. Reuses the in-conversation
 * {@link KnowledgeSearchTools} verbatim, with fixed results/length — no per-call config, no auth.
 * Relies on the app being bound to localhost (see {@code server.address} in application.properties).
 */
@Configuration
public class McpServerConfig {

    // POC defaults; the in-conversation tool reads these from settings, but a fixed pair keeps the
    // server independent of the chat search-mode toggle.
    private static final int RESULTS = 5;
    private static final int RESULT_LENGTH = 3000;

    @Bean
    public ToolCallbackProvider cdaMcpTools(KnowledgeSearchService knowledgeSearchService) {
        KnowledgeSearchTools tools = new KnowledgeSearchTools(knowledgeSearchService, RESULTS, RESULT_LENGTH);
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    // Guard the MCP transport endpoints (SSE stream + message post) against DNS-rebinding by
    // rejecting any request whose Host/Origin is not loopback. Highest precedence so it runs
    // before the request reaches the transport handler.
    @Bean
    public FilterRegistrationBean<McpLocalOnlyFilter> mcpLocalOnlyFilter() {
        FilterRegistrationBean<McpLocalOnlyFilter> registration = new FilterRegistrationBean<>(new McpLocalOnlyFilter());
        registration.addUrlPatterns("/sse", "/mcp/*");
        registration.setName("mcpLocalOnlyFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
