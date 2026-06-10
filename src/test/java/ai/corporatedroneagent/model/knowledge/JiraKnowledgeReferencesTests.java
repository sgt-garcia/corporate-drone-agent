package ai.corporatedroneagent.model.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JiraKnowledgeReferencesTests {

    @Test
    void projectRootReferenceUsesNormalizedInstanceAndStableProjectId() {
        assertThat(JiraKnowledgeReferences.projectRootReference(
                "https://Example.atlassian.net/",
                "10001"
        )).isEqualTo("jira://example.atlassian.net/project/10001");
    }

    @Test
    void referencesPreserveServerContextPathAndEncodeIds() {
        assertThat(JiraKnowledgeReferences.projectRootReference(
                "https://jira.example.com/jira/",
                "Project 10001"
        )).isEqualTo("jira://jira.example.com/jira/project/Project%2010001");

        assertThat(JiraKnowledgeReferences.issueResourceReference(
                "https://jira.example.com/jira/",
                "Issue 12345"
        )).isEqualTo("jira://jira.example.com/jira/issue/Issue%2012345");
    }
}
