package ai.corporatedroneagent.service;

import ai.corporatedroneagent.util.Strings;

/**
 * Plain-text line accumulation shared by the Confluence/Jira fetch services, which render
 * a resource into {@code label: value} lines.
 */
final class MarkdownLines {

    private MarkdownLines() {
    }

    static void appendLine(StringBuilder builder, String line) {
        builder.append(line).append('\n');
    }

    static void appendField(StringBuilder builder, String label, String value) {
        String trimmed = Strings.defaultIfBlank(value, "").trim();
        if (!trimmed.isBlank()) {
            appendLine(builder, label + ": " + trimmed);
        }
    }
}
