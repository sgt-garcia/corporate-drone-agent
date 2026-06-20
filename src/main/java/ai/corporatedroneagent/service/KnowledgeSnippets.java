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
        String source = Strings.defaultIfBlank(snippet.source(), "").trim().toUpperCase(Locale.ROOT);
        return switch (source) {
            case "JIRA" -> "Jira";
            case "LOCAL_FOLDER" -> "Local folder";
            default -> "Knowledge";
        };
    }

    private static String resourceLabel(KnowledgeContextSnippet snippet) {
        String source = Strings.defaultIfBlank(snippet.source(), "").trim().toUpperCase(Locale.ROOT);
        if ("JIRA".equals(source)) {
            return Strings.defaultIfBlank(snippet.resourceName(), snippet.resourceReference());
        }
        return Strings.defaultIfBlank(snippet.resourceReference(), snippet.resourceName());
    }
}
