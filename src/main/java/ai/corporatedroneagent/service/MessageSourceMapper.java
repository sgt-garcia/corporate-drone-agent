package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.MessageSourceDto;
import ai.corporatedroneagent.util.Strings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the knowledge snippets consulted for a reply into the source pills the
 * workspace shows. Multiple chunks from the same resource collapse to one pill,
 * keeping the first (highest-ranked) chunk's text as the excerpt and preserving
 * retrieval order.
 */
final class MessageSourceMapper {

    private static final int MAX_EXCERPT = 500;

    private MessageSourceMapper() {
    }

    static List<MessageSourceDto> toSources(List<KnowledgeContextSnippet> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return List.of();
        }
        Map<String, KnowledgeContextSnippet> byResource = new LinkedHashMap<>();
        for (KnowledgeContextSnippet snippet : snippets) {
            byResource.putIfAbsent(resourceKey(snippet), snippet);
        }
        List<MessageSourceDto> sources = new ArrayList<>();
        int index = 0;
        for (KnowledgeContextSnippet snippet : byResource.values()) {
            boolean jira = isJira(snippet);
            sources.add(new MessageSourceDto(
                    "s" + index++,
                    jira ? "thread" : "doc",
                    jira ? "ticket" : "file-text",
                    title(snippet),
                    meta(snippet, jira),
                    "",
                    "",
                    new MessageSourceDto.Preview(excerpt(snippet))
            ));
        }
        return sources;
    }

    private static String resourceKey(KnowledgeContextSnippet snippet) {
        return Strings.defaultIfBlank(snippet.source(), "") + "::"
                + Strings.defaultIfBlank(snippet.resourceReference(), snippet.resourceName());
    }

    private static boolean isJira(KnowledgeContextSnippet snippet) {
        return "JIRA".equals(Strings.defaultIfBlank(snippet.source(), "").trim().toUpperCase(Locale.ROOT));
    }

    private static String title(KnowledgeContextSnippet snippet) {
        return Strings.defaultIfBlank(
                snippet.resourceName(),
                Strings.defaultIfBlank(snippet.resourceReference(), "Untitled source"));
    }

    private static String meta(KnowledgeContextSnippet snippet, boolean jira) {
        String label = jira ? "Jira" : "Local folder";
        return label + " · " + Strings.defaultIfBlank(snippet.rootName(), label);
    }

    private static String excerpt(KnowledgeContextSnippet snippet) {
        String content = snippet.content() == null ? "" : snippet.content().trim();
        if (content.length() <= MAX_EXCERPT) {
            return content;
        }
        return content.substring(0, MAX_EXCERPT).trim() + "…";
    }
}
