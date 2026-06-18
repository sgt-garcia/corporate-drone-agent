package ai.corporatedroneagent.model.knowledge;

import ai.corporatedroneagent.util.Strings;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class ConfluenceKnowledgeReferences {

    private ConfluenceKnowledgeReferences() {
    }

    public static String spaceRootReference(String instanceUrl, String spaceId) {
        return scopedReference(instanceUrl, "space", spaceId);
    }

    public static String pageResourceReference(String instanceUrl, String pageId) {
        return scopedReference(instanceUrl, "page", pageId);
    }

    public static String normalizedInstanceScope(String instanceUrl) {
        String trimmed = Strings.defaultIfBlank(instanceUrl, "").trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        URI uri = URI.create(trimmed);
        String authority = Strings.defaultIfBlank(uri.getAuthority(), "").toLowerCase(Locale.ROOT);
        String path = Strings.defaultIfBlank(uri.getPath(), "");
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return authority + path;
    }

    private static String scopedReference(String instanceUrl, String scope, String id) {
        String normalizedId = Strings.defaultIfBlank(id, "").trim();
        if (normalizedId.isBlank()) {
            throw new IllegalArgumentException("Confluence reference id is required");
        }
        return "confluence://" + normalizedInstanceScope(instanceUrl) + "/" + scope + "/" + encodePathSegment(normalizedId);
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
