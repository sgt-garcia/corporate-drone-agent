package ai.corporatedroneagent.model.knowledge;

/**
 * Reads and writes the Confluence space identity (id/key/name) carried in a CONFLUENCE
 * {@link KnowledgeRoot}'s {@code configJson}, so the root is the sole record for a space —
 * mirroring {@link JiraKnowledgeRootConfig} for Jira projects. The shared read/write logic
 * lives in {@link KnowledgeRootConfigSupport}.
 */
public final class ConfluenceKnowledgeRootConfig {

    private static final KnowledgeRootConfigSupport SUPPORT = new KnowledgeRootConfigSupport(
            "confluenceSpaceId", "confluenceSpaceKey", "confluenceSpaceName", "/space/");

    private ConfluenceKnowledgeRootConfig() {
    }

    public static String readSpaceId(KnowledgeRoot root) {
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
