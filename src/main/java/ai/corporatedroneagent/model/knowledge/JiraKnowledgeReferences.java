package ai.corporatedroneagent.model.knowledge;

public final class JiraKnowledgeReferences {

    private JiraKnowledgeReferences() {
    }

    public static String projectRootReference(String instanceUrl, String projectId) {
        return KnowledgeReferences.scopedReference("jira", "Jira", instanceUrl, "project", projectId);
    }

    public static String issueResourceReference(String instanceUrl, String issueId) {
        return KnowledgeReferences.scopedReference("jira", "Jira", instanceUrl, "issue", issueId);
    }
}
