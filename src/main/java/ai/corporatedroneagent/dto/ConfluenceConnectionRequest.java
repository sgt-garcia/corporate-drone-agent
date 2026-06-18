package ai.corporatedroneagent.dto;

public record ConfluenceConnectionRequest(
        String instanceUrl,
        String email,
        String token,
        boolean clearToken
) {
}
