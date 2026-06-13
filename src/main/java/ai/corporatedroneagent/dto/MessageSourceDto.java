package ai.corporatedroneagent.dto;

/**
 * A source the agent consulted to produce a reply, surfaced as a clickable pill
 * under the message ("show your sources"). Shape mirrors what the workspace UI
 * renders: a kind/icon-tinted pill that expands into an excerpt preview.
 */
public record MessageSourceDto(
        String id,
        String kind,
        String icon,
        String title,
        String meta,
        String url,
        String actionLabel,
        Preview preview
) {
    public record Preview(String excerpt) {
    }
}
