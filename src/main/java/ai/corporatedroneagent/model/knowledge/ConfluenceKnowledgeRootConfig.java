package ai.corporatedroneagent.model.knowledge;

import ai.corporatedroneagent.util.Strings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Reads and writes the Confluence space identity (id/key/name) carried in a CONFLUENCE
 * {@link KnowledgeRoot}'s {@code configJson}, so the root is the sole record for a space —
 * mirroring {@link JiraKnowledgeRootConfig} for Jira projects.
 *
 * <p>The writer merges into the existing config (the blob is shared with the scan cursor
 * metadata written by the scan engine), touching only the identity keys. The readers fall
 * back to the root reference / display name so a root created before the identity was
 * stored still resolves.
 */
public final class ConfluenceKnowledgeRootConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SPACE_ID = "confluenceSpaceId";
    private static final String SPACE_KEY = "confluenceSpaceKey";
    private static final String SPACE_NAME = "confluenceSpaceName";
    private static final String SPACE_PATH_SEGMENT = "/space/";
    private static final String DISPLAY_NAME_SEPARATOR = " - ";

    private ConfluenceKnowledgeRootConfig() {
    }

    public static String readSpaceId(KnowledgeRoot root) {
        String configured = readField(root, SPACE_ID);
        return configured.isBlank() ? referenceId(root) : configured;
    }

    public static String readKey(KnowledgeRoot root) {
        String configured = readField(root, SPACE_KEY);
        return configured.isBlank() ? displayNamePart(root, true) : configured;
    }

    public static String readName(KnowledgeRoot root) {
        String configured = readField(root, SPACE_NAME);
        return configured.isBlank() ? displayNamePart(root, false) : configured;
    }

    /**
     * Returns {@code configJson} with the identity keys set, preserving every other key
     * already present (notably the scan cursor).
     */
    public static String withIdentity(String configJson, String id, String key, String name) {
        ObjectNode config = parse(configJson);
        config.put(SPACE_ID, Strings.defaultIfBlank(id, "").trim());
        config.put(SPACE_KEY, Strings.defaultIfBlank(key, "").trim());
        config.put(SPACE_NAME, Strings.defaultIfBlank(name, "").trim());
        return config.toString();
    }

    private static String readField(KnowledgeRoot root, String field) {
        if (root == null) {
            return "";
        }
        return parse(root.getConfigJson()).path(field).asText("").trim();
    }

    private static ObjectNode parse(String configJson) {
        String trimmed = Strings.defaultIfBlank(configJson, "").trim();
        if (!trimmed.isBlank()) {
            try {
                if (MAPPER.readTree(trimmed) instanceof ObjectNode existing) {
                    return existing;
                }
            } catch (IOException ignored) {
                // Fall through to a fresh node — a malformed blob shouldn't break reads.
            }
        }
        return MAPPER.createObjectNode();
    }

    private static String referenceId(KnowledgeRoot root) {
        if (root == null) {
            return "";
        }
        String reference = Strings.defaultIfBlank(root.getReference(), "");
        int marker = reference.lastIndexOf(SPACE_PATH_SEGMENT);
        if (marker < 0) {
            return "";
        }
        String encoded = reference.substring(marker + SPACE_PATH_SEGMENT.length());
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    private static String displayNamePart(KnowledgeRoot root, boolean keyPart) {
        if (root == null) {
            return "";
        }
        String displayName = Strings.defaultIfBlank(root.getDisplayName(), "").trim();
        int separator = displayName.indexOf(DISPLAY_NAME_SEPARATOR);
        if (separator < 0) {
            return keyPart ? displayName : "";
        }
        return keyPart
                ? displayName.substring(0, separator)
                : displayName.substring(separator + DISPLAY_NAME_SEPARATOR.length());
    }
}
