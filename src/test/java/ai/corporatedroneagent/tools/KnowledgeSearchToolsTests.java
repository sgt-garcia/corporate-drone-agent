package ai.corporatedroneagent.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.corporatedroneagent.service.KnowledgeContextSnippet;
import ai.corporatedroneagent.service.KnowledgeSearchService;
import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeSearchToolsTests {

    @Test
    void formatsRetrievedSnippetsWithItsConfiguredBounds() {
        KnowledgeSearchService search = mock(KnowledgeSearchService.class);
        when(search.search("renewal terms", 5, 2000)).thenReturn(List.of(
                new KnowledgeContextSnippet(
                        "JIRA",
                        "DEV - Software Development",
                        "DEV-77",
                        "DEV-77 - Checkout telemetry regression",
                        0,
                        "Renewal terms are net-30.",
                        1.0f)));
        KnowledgeSearchTools tools = new KnowledgeSearchTools(search, 5, 2000);

        String result = tools.searchKnowledge("renewal terms");

        assertThat(result)
                .contains("[1]")
                .contains("Jira / DEV - Software Development / DEV-77 - Checkout telemetry regression")
                .contains("Renewal terms are net-30.");
    }

    @Test
    void degradesGracefullyWhenSearchFails() {
        KnowledgeSearchService search = mock(KnowledgeSearchService.class);
        when(search.search("boom", 5, 2000)).thenThrow(new RuntimeException("index unavailable"));
        KnowledgeSearchTools tools = new KnowledgeSearchTools(search, 5, 2000);

        assertThat(tools.searchKnowledge("boom"))
                .startsWith("Knowledge search failed");
    }

    @Test
    void reportsWhenNothingMatches() {
        KnowledgeSearchService search = mock(KnowledgeSearchService.class);
        when(search.search("missing", 5, 2000)).thenReturn(List.of());
        KnowledgeSearchTools tools = new KnowledgeSearchTools(search, 5, 2000);

        assertThat(tools.searchKnowledge("missing"))
                .isEqualTo("No matching knowledge was found for that query.");
    }
}
