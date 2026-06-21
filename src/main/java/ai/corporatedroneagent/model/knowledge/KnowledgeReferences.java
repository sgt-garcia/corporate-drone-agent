package ai.corporatedroneagent.model.knowledge;

import ai.corporatedroneagent.util.Strings;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Shared building blocks for the {@code <scheme>://<instance-scope>/<scope>/<id>} references
 * used by {@link ConfluenceKnowledgeReferences} and {@link JiraKnowledgeReferences}, which
 * differ only in their URI scheme.
 */
final class KnowledgeReferences {

    private KnowledgeReferences() {
    }

    static String scopedReference(String scheme, String sourceName, String instanceUrl, String scope, String id) {
        String normalizedId = Strings.defaultIfBlank(id, "").trim();
        if (normalizedId.isBlank()) {
            throw new IllegalArgumentException(sourceName + " reference id is required");
        }
        return scheme + "://" + normalizedInstanceScope(instanceUrl) + "/" + scope + "/" + encodePathSegment(normalizedId);
    }

    static String normalizedInstanceScope(String instanceUrl) {
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

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
