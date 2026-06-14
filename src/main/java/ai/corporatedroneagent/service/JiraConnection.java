package ai.corporatedroneagent.service;

/** The resolved Jira connection inputs a scan needs, independent of where they're stored. */
public record JiraConnection(String instanceUrl, String email, String apiVersion, String token) {
}
