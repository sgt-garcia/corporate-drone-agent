package ai.corporatedroneagent.model.knowledge;

import ai.corporatedroneagent.util.Strings;
import java.net.URI;
import java.util.Locale;

public final class ConfluenceKnowledgeReferences {

    private ConfluenceKnowledgeReferences() {
    }

    public static String spaceRootReference(String instanceUrl, String spaceId) {
        return KnowledgeReferences.scopedReference("confluence", "Confluence", instanceUrl, "space", spaceId);
    }

    public static String pageResourceReference(String instanceUrl, String pageId) {
        return KnowledgeReferences.scopedReference("confluence", "Confluence", instanceUrl, "page", pageId);
    }

    /**
     * The REST API base for a Confluence instance URL. Atlassian Cloud serves Confluence
     * under a {@code /wiki} context path, so a bare {@code https://site.atlassian.net} is
     * normalized to {@code https://site.atlassian.net/wiki} — users routinely paste the host
     * without it. Server / Data Center URLs (any non-atlassian.net host) are used as entered,
     * since their context path varies. The result never ends in a trailing slash.
     */
    public static String apiBaseUrl(String instanceUrl) {
        String trimmed = Strings.defaultIfBlank(instanceUrl, "").trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isBlank()) {
            return trimmed;
        }
        try {
            URI uri = URI.create(trimmed);
            String host = Strings.defaultIfBlank(uri.getHost(), "").toLowerCase(Locale.ROOT);
            String path = Strings.defaultIfBlank(uri.getPath(), "").toLowerCase(Locale.ROOT);
            boolean cloud = host.endsWith(".atlassian.net");
            boolean hasWiki = path.equals("/wiki") || path.startsWith("/wiki/");
            if (cloud && !hasWiki) {
                return trimmed + "/wiki";
            }
        } catch (IllegalArgumentException ignored) {
            // Not a parseable URI — return it as-is; the HTTP call will surface the error.
        }
        return trimmed;
    }
}
