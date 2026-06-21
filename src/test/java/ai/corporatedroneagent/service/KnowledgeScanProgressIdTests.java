package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ai.corporatedroneagent.model.knowledge.ConfluenceKnowledgeRootConfig;
import ai.corporatedroneagent.model.knowledge.JiraKnowledgeRootConfig;
import ai.corporatedroneagent.model.knowledge.KnowledgeRoot;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Each source must key the live "scanning X" ticker on the id its settings row uses, or the
 * progress never reaches the right row. A folder row keys on the root id, but a Jira project /
 * Confluence space row keys on the external project/space id — not the root id. Regression guard
 * for the unification that briefly emitted the root id for every source, blanking the Jira and
 * Confluence tickers.
 */
class KnowledgeScanProgressIdTests {

    @Test
    void folderKeysProgressOnRootIdMatchingItsSettingsRow() {
        FolderSourceAdapter adapter = new FolderSourceAdapter(
                mock(LocalFolderKnowledgeReadService.class),
                mock(LocalFolderKnowledgeConversionService.class));
        KnowledgeRoot root = new KnowledgeRoot();
        UUID rootId = UUID.randomUUID();
        root.setId(rootId);

        assertThat(adapter.scanProgressId(root)).isEqualTo(rootId.toString());
    }

    @Test
    void jiraKeysProgressOnExternalProjectIdNotRootId() {
        JiraSourceAdapter adapter = new JiraSourceAdapter(
                mock(JiraIssueFetchService.class),
                mock(JiraConnectionResolver.class));
        KnowledgeRoot root = new KnowledgeRoot();
        root.setId(UUID.randomUUID());
        root.setConfigJson(JiraKnowledgeRootConfig.withIdentity(null, "10001", "DEV", "Software Development"));

        assertThat(adapter.scanProgressId(root)).isEqualTo("10001");
    }

    @Test
    void confluenceKeysProgressOnExternalSpaceIdNotRootId() {
        ConfluenceSourceAdapter adapter = new ConfluenceSourceAdapter(
                mock(ConfluencePageFetchService.class),
                mock(ConfluenceConnectionResolver.class));
        KnowledgeRoot root = new KnowledgeRoot();
        root.setId(UUID.randomUUID());
        root.setConfigJson(ConfluenceKnowledgeRootConfig.withIdentity(null, "98765", "ENG", "Engineering"));

        assertThat(adapter.scanProgressId(root)).isEqualTo("98765");
    }
}
