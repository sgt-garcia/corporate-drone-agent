package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import ai.corporatedroneagent.dto.MessageSourceDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageSourceMapperTests {

    @Test
    void mapsLocalFolderAndJiraSnippetsToPills() {
        List<MessageSourceDto> sources = MessageSourceMapper.toSources(List.of(
                new KnowledgeContextSnippet(
                        "LOCAL_FOLDER", "Q2 ops", "~/work/q2/plan.md", "plan.md", 0, "Spend is on track.", 1.0f),
                new KnowledgeContextSnippet(
                        "JIRA", "Operations", "OPS-1423", "Invoice reconciliation", 0, "Two invoices await a PO.", 0.9f)
        ));

        assertThat(sources).hasSize(2);
        assertThat(sources.get(0).kind()).isEqualTo("doc");
        assertThat(sources.get(0).icon()).isEqualTo("file-text");
        assertThat(sources.get(0).title()).isEqualTo("plan.md");
        assertThat(sources.get(0).meta()).isEqualTo("Local folder · Q2 ops");
        assertThat(sources.get(0).preview().excerpt()).isEqualTo("Spend is on track.");
        assertThat(sources.get(1).kind()).isEqualTo("thread");
        assertThat(sources.get(1).icon()).isEqualTo("ticket");
        assertThat(sources.get(1).title()).isEqualTo("Invoice reconciliation");
        assertThat(sources.get(1).meta()).isEqualTo("Jira · Operations");
    }

    @Test
    void collapsesMultipleChunksFromTheSameResourceKeepingTheTopRanked() {
        List<MessageSourceDto> sources = MessageSourceMapper.toSources(List.of(
                new KnowledgeContextSnippet(
                        "LOCAL_FOLDER", "Q2 ops", "~/work/q2/plan.md", "plan.md", 0, "First chunk.", 1.0f),
                new KnowledgeContextSnippet(
                        "LOCAL_FOLDER", "Q2 ops", "~/work/q2/plan.md", "plan.md", 1, "Second chunk.", 0.8f)
        ));

        assertThat(sources).singleElement()
                .satisfies(source -> assertThat(source.preview().excerpt()).isEqualTo("First chunk."));
    }

    @Test
    void truncatesAnOverlongExcerpt() {
        String content = "x".repeat(600);
        List<MessageSourceDto> sources = MessageSourceMapper.toSources(List.of(
                new KnowledgeContextSnippet("LOCAL_FOLDER", "Docs", "a.md", "a.md", 0, content, 1.0f)));

        assertThat(sources.get(0).preview().excerpt()).hasSize(501).endsWith("…");
    }

    @Test
    void returnsEmptyForNoSnippets() {
        assertThat(MessageSourceMapper.toSources(List.of())).isEmpty();
        assertThat(MessageSourceMapper.toSources(null)).isEmpty();
    }
}
