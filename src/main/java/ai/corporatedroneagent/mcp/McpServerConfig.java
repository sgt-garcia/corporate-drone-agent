package ai.corporatedroneagent.mcp;

import ai.corporatedroneagent.service.KnowledgeSearchService;
import ai.corporatedroneagent.service.SettingsService;
import ai.corporatedroneagent.tools.KnowledgeSearchTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Exposes the on-demand knowledge tools (search, fetch a full document, list sources) over MCP so
 * external clients (e.g. Claude) can query the user's connected knowledge sources. Reuses the
 * in-conversation {@link KnowledgeSearchTools}; search_knowledge reads its result count and length
 * from the Settings → Knowledge search-mode config per call. Reachability is gated by the filters
 * below (mcpServerEnabled + loopback-only).
 */
@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider cdaMcpTools(
            KnowledgeSearchService knowledgeSearchService, SettingsService settingsService) {
        KnowledgeSearchTools tools =
                new KnowledgeSearchTools(knowledgeSearchService, settingsService::knowledgeSearchMode);
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    // Gate the MCP transport endpoints on the mcpServerEnabled setting so the Settings → Tools
    // toggle disables the server at runtime. Runs first (before the loopback guard) so a disabled
    // server answers 503 regardless of where the request came from.
    @Bean
    public FilterRegistrationBean<McpServerEnabledFilter> mcpServerEnabledFilter(SettingsService settingsService) {
        FilterRegistrationBean<McpServerEnabledFilter> registration =
                new FilterRegistrationBean<>(new McpServerEnabledFilter(settingsService::isMcpServerEnabled));
        registration.addUrlPatterns("/sse", "/mcp/*");
        registration.setName("mcpServerEnabledFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    // Guard the MCP transport endpoints (SSE stream + message post) against DNS-rebinding by
    // rejecting any request whose Host/Origin is not loopback. Runs just after the enabled gate.
    @Bean
    public FilterRegistrationBean<McpLocalOnlyFilter> mcpLocalOnlyFilter() {
        FilterRegistrationBean<McpLocalOnlyFilter> registration = new FilterRegistrationBean<>(new McpLocalOnlyFilter());
        registration.addUrlPatterns("/sse", "/mcp/*");
        registration.setName("mcpLocalOnlyFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
