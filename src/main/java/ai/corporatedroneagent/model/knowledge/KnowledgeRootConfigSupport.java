package ai.corporatedroneagent.model.knowledge;

import ai.corporatedroneagent.util.Strings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Shared read/write logic for the source identity (id/key/name) carried in a
 * {@link KnowledgeRoot}'s {@code configJson}. {@link ConfluenceKnowledgeRootConfig} and
 * {@link JiraKnowledgeRootConfig} differ only in the three identity key names and the
 * reference path segment, so they delegate here.
 *
 * <p>The writer merges into the existing config (the blob is shared with the scan cursor
 * metadata written by the scan engine), touching only the identity keys. The readers fall
 * back to the root reference / display name so a root created before the identity was
 * stored still resolves.
 */
final class KnowledgeRootConfigSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DISPLAY_NAME_SEPARATOR = " - ";

    private final String idKey;
    private final String keyKey;
    private final String nameKey;
    private final String pathSegment;

    KnowledgeRootConfigSupport(String idKey, String keyKey, String nameKey, String pathSegment) {
        this.idKey = idKey;
        this.keyKey = keyKey;
        this.nameKey = nameKey;
        this.pathSegment = pathSegment;
    }

    String readId(KnowledgeRoot root) {
        String configured = readField(root, idKey);
        return configured.isBlank() ? referenceId(root) : configured;
    }

    String readKey(KnowledgeRoot root) {
        String configured = readField(root, keyKey);
        return configured.isBlank() ? displayNamePart(root, true) : configured;
    }

    String readName(KnowledgeRoot root) {
        String configured = readField(root, nameKey);
        return configured.isBlank() ? displayNamePart(root, false) : configured;
    }

    String withIdentity(String configJson, String id, String key, String name) {
        ObjectNode config = parse(configJson);
        config.put(idKey, Strings.defaultIfBlank(id, "").trim());
        config.put(keyKey, Strings.defaultIfBlank(key, "").trim());
        config.put(nameKey, Strings.defaultIfBlank(name, "").trim());
        return config.toString();
    }

    private String readField(KnowledgeRoot root, String field) {
        if (root == null) {
            return "";
        }
        return parse(root.getConfigJson()).path(field).asText("").trim();
    }

    private ObjectNode parse(String configJson) {
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

    private String referenceId(KnowledgeRoot root) {
        if (root == null) {
            return "";
        }
        String reference = Strings.defaultIfBlank(root.getReference(), "");
        int marker = reference.lastIndexOf(pathSegment);
        if (marker < 0) {
            return "";
        }
        String encoded = reference.substring(marker + pathSegment.length());
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }

    private String displayNamePart(KnowledgeRoot root, boolean keyPart) {
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
