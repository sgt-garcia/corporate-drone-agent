package ai.corporatedroneagent.service;

import ai.corporatedroneagent.util.Strings;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared field validation for Jira/Confluence connection requests: the two requests carry the same
 * instanceUrl/email/token shape and identical rules, differing only in the product name woven into
 * each message. The null-request check stays with each caller (its message differs too).
 */
final class AtlassianConnectionValidation {

    private AtlassianConnectionValidation() {
    }

    static void validateConnectionDetails(
            String product, String instanceUrlRaw, String emailRaw, String tokenRaw, boolean hasSavedToken) {
        String instanceUrl = Strings.defaultIfBlank(instanceUrlRaw, "");
        if (instanceUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, product + " instance URL is required");
        }
        if (!instanceUrl.startsWith("https://") && !instanceUrl.startsWith("http://")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, product + " instance URL must start with https://");
        }
        String email = Strings.defaultIfBlank(emailRaw, "");
        if (email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, product + " email is required");
        }
        if (!email.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, product + " email must be valid");
        }
        String token = Strings.defaultIfBlank(tokenRaw, "");
        if (token.isBlank() && !hasSavedToken) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, product + " API token is required");
        }
    }
}
