package ai.corporatedroneagent.model.knowledge;

import ai.corporatedroneagent.util.Strings;

public final class JiraKnowledgeReferences {

    private JiraKnowledgeReferences() {
    }

    public static String projectRootReference(String instanceUrl, String projectId) {
        return KnowledgeReferences.scopedReference("jira", "Jira", instanceUrl, "project", projectId);
    }

    public static String issueResourceReference(String instanceUrl, String issueId) {
        return KnowledgeReferences.scopedReference("jira", "Jira", instanceUrl, "issue", issueId);
    }

    /**
     * The REST API base for a Jira instance URL: the instance URL with any trailing slashes
     * removed. Jira has no Cloud context path (unlike Confluence's {@code /wiki}), so the URL is
     * used as entered. Mirrors {@link ConfluenceKnowledgeReferences#apiBaseUrl}.
     */
    public static String apiBaseUrl(String instanceUrl) {
        return Strings.trimTrailingSlashes(Strings.defaultIfBlank(instanceUrl, "").trim());
    }
}
