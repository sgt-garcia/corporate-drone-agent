package ai.corporatedroneagent.service;

/** The resolved Confluence connection inputs a scan needs, independent of where they're stored. */
public record ConfluenceConnection(String instanceUrl, String email, String token) {
}
