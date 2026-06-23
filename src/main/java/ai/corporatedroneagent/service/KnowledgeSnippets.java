package ai.corporatedroneagent.service;

import ai.corporatedroneagent.util.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared formatting for retrieved knowledge, used by automatic retrieval (injected into the
 * prompt), the on-demand search tool, and the fetch-document tool, so every path labels and
 * fences knowledge identically.
 */
public final class KnowledgeSnippets {

    private KnowledgeSnippets() {
    }

    // Numbered, source-labelled, fenced blocks joined by blank lines. No intro line — each
    // caller adds its own framing.
    public static String formatBlocks(List<KnowledgeContextSnippet> snippets) {
        List<String> blocks = new ArrayList<>();
        for (int index = 0; index < snippets.size(); index++) {
            KnowledgeContextSnippet snippet = snippets.get(index);
            blocks.add("[" + (index + 1) + "] " + label(snippet)
                    + "\n```\n" + snippet.content().trim() + "\n```");
        }
        return String.join("\n\n", blocks);
    }

    public static String label(KnowledgeContextSnippet snippet) {
        return label(snippet.source(), snippet.rootName(), snippet.resourceReference(), snippet.resourceName());
    }

    // The "source / root / resource" heading from raw fields, so snippets and fetched documents
    // (which carry the same identifying fields but different tails) render an identical label.
    public static String label(String source, String rootName, String resourceReference, String resourceName) {
        return sourceDisplay(source)
                + " / " + Strings.defaultIfBlank(rootName, "Knowledge")
                + " / " + resourceLabel(source, resourceReference, resourceName);
    }

    // Human-friendly name for a KnowledgeSource enum value, shared so snippets, fetched documents,
    // and the source listing all label sources identically.
    public static String sourceDisplay(String source) {
        return switch (sourceKey(source)) {
            case "JIRA" -> "Jira";
            case "CONFLUENCE" -> "Confluence";
            case "LOCAL_FOLDER" -> "Local folder";
            default -> "Knowledge";
        };
    }

    // Jira and Confluence carry the human-friendly title in resourceName; the reference is an
    // issue key or page URL. Local folders carry the file path in the reference instead.
    private static String resourceLabel(String source, String resourceReference, String resourceName) {
        return switch (sourceKey(source)) {
            case "JIRA", "CONFLUENCE" -> Strings.defaultIfBlank(resourceName, resourceReference);
            default -> Strings.defaultIfBlank(resourceReference, resourceName);
        };
    }

    private static String sourceKey(String source) {
        return Strings.defaultIfBlank(source, "").trim().toUpperCase(Locale.ROOT);
    }
}
