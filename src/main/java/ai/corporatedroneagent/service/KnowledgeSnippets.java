package ai.corporatedroneagent.service;

import ai.corporatedroneagent.util.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared formatting for retrieved knowledge snippets, used by both automatic retrieval
 * (injected into the prompt) and the on-demand search tool (returned as a tool result),
 * so the two paths label and fence snippets identically.
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
        return sourceLabel(snippet)
                + " / " + Strings.defaultIfBlank(snippet.rootName(), "Knowledge")
                + " / " + resourceLabel(snippet);
    }

    private static String sourceLabel(KnowledgeContextSnippet snippet) {
        return sourceDisplay(snippet.source());
    }

    // Human-friendly name for a KnowledgeSource enum value, shared so snippets, fetched documents,
    // and the source listing all label sources identically.
    public static String sourceDisplay(String source) {
        return switch (Strings.defaultIfBlank(source, "").trim().toUpperCase(Locale.ROOT)) {
            case "JIRA" -> "Jira";
            case "CONFLUENCE" -> "Confluence";
            case "LOCAL_FOLDER" -> "Local folder";
            default -> "Knowledge";
        };
    }

    // Jira and Confluence carry the human-friendly title in resourceName; the reference is an
    // issue key or page URL. Local folders carry the file path in the reference instead.
    private static String resourceLabel(KnowledgeContextSnippet snippet) {
        return switch (sourceKey(snippet)) {
            case "JIRA", "CONFLUENCE" -> Strings.defaultIfBlank(snippet.resourceName(), snippet.resourceReference());
            default -> Strings.defaultIfBlank(snippet.resourceReference(), snippet.resourceName());
        };
    }

    private static String sourceKey(KnowledgeContextSnippet snippet) {
        return Strings.defaultIfBlank(snippet.source(), "").trim().toUpperCase(Locale.ROOT);
    }
}
