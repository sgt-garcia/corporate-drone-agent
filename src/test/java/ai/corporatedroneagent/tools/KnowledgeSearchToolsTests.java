package ai.corporatedroneagent.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.corporatedroneagent.service.KnowledgeContextSnippet;
import ai.corporatedroneagent.service.KnowledgeDocument;
import ai.corporatedroneagent.service.KnowledgeSearchService;
import ai.corporatedroneagent.service.KnowledgeSourceSummary;
import java.util.List;
import java.util.Optional;
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

    @Test
    void fetchReturnsTheFullLabelledDocument() {
        KnowledgeSearchService search = mock(KnowledgeSearchService.class);
        when(search.fetchDocument("DEV-77", 50_000)).thenReturn(Optional.of(new KnowledgeDocument(
                "JIRA",
                "DEV - Software Development",
                "DEV-77",
                "DEV-77 - Checkout telemetry regression",
                "Full issue body with reproduction steps and acceptance criteria.",
                false,
                64)));
        KnowledgeSearchTools tools = new KnowledgeSearchTools(search, 5, 2000);

        String result = tools.fetchKnowledgeDocument("DEV-77");

        assertThat(result)
                .contains("Knowledge document")
                .contains("Jira / DEV - Software Development / DEV-77 - Checkout telemetry regression")
                .contains("Full issue body with reproduction steps and acceptance criteria.")
                .doesNotContain("Showing the first");
    }

    @Test
    void fetchNotesWhenTheDocumentIsTruncated() {
        KnowledgeSearchService search = mock(KnowledgeSearchService.class);
        when(search.fetchDocument("notes.md", 50_000)).thenReturn(Optional.of(new KnowledgeDocument(
                "LOCAL_FOLDER",
                "Team drive",
                "/team/notes.md",
                "notes.md",
                "First fifty thousand characters …",
                true,
                120_000)));
        KnowledgeSearchTools tools = new KnowledgeSearchTools(search, 5, 2000);

        assertThat(tools.fetchKnowledgeDocument("notes.md"))
                .contains("Showing the first 50000 of 120000 characters");
    }

    @Test
    void fetchReportsWhenNoDocumentMatches() {
        KnowledgeSearchService search = mock(KnowledgeSearchService.class);
        when(search.fetchDocument("ghost.txt", 50_000)).thenReturn(Optional.empty());
        KnowledgeSearchTools tools = new KnowledgeSearchTools(search, 5, 2000);

        assertThat(tools.fetchKnowledgeDocument("ghost.txt"))
                .contains("No knowledge document matched \"ghost.txt\"")
                .contains("search_knowledge");
    }

    @Test
    void fetchDegradesGracefullyWhenItFails() {
        KnowledgeSearchService search = mock(KnowledgeSearchService.class);
        when(search.fetchDocument("boom", 50_000)).thenThrow(new RuntimeException("index unavailable"));
        KnowledgeSearchTools tools = new KnowledgeSearchTools(search, 5, 2000);

        assertThat(tools.fetchKnowledgeDocument("boom"))
                .startsWith("Knowledge fetch failed");
    }

    @Test
    void listsConnectedSourcesWithCountsAndStatus() {
        KnowledgeSearchService search = mock(KnowledgeSearchService.class);
        when(search.listSources()).thenReturn(List.of(
                new KnowledgeSourceSummary("JIRA", "DEV - Software Development", 128, "DONE", false),
                new KnowledgeSourceSummary("LOCAL_FOLDER", "Team drive", 1, "IN_PROGRESS", true)));
        KnowledgeSearchTools tools = new KnowledgeSearchTools(search, 5, 2000);

        String result = tools.listKnowledgeSources();

        assertThat(result)
                .contains("Jira / DEV - Software Development — 128 documents, scan DONE")
                .contains("Local folder / Team drive — 1 document, scan IN_PROGRESS (paused)");
    }

    @Test
    void reportsWhenNoSourcesAreConnected() {
        KnowledgeSearchService search = mock(KnowledgeSearchService.class);
        when(search.listSources()).thenReturn(List.of());
        KnowledgeSearchTools tools = new KnowledgeSearchTools(search, 5, 2000);

        assertThat(tools.listKnowledgeSources())
                .isEqualTo("No knowledge sources are connected.");
    }
}
