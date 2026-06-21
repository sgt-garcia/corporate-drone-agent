package ai.corporatedroneagent.model.knowledge;

/**
 * Reads and writes the Jira project identity (id/key/name) carried in a JIRA
 * {@link KnowledgeRoot}'s {@code configJson}, so the root is the sole record for a
 * project — mirroring how a local folder root is the sole record for a folder. The shared
 * read/write logic lives in {@link KnowledgeRootConfigSupport}.
 */
public final class JiraKnowledgeRootConfig {

    private static final KnowledgeRootConfigSupport SUPPORT = new KnowledgeRootConfigSupport(
            "jiraProjectId", "jiraProjectKey", "jiraProjectName", "/project/");

    private JiraKnowledgeRootConfig() {
    }

    public static String readProjectId(KnowledgeRoot root) {
        return SUPPORT.readId(root);
    }

    public static String readKey(KnowledgeRoot root) {
        return SUPPORT.readKey(root);
    }

    public static String readName(KnowledgeRoot root) {
        return SUPPORT.readName(root);
    }

    public static String withIdentity(String configJson, String id, String key, String name) {
        return SUPPORT.withIdentity(configJson, id, key, name);
    }
}
