package ai.corporatedroneagent.service;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Shared HTTP primitives for the Confluence/Jira REST services: the client builder,
 * basic-auth header, and URL encoding. The status-code handling stays in each service,
 * since the user-facing error messages are specific to what each call is doing.
 */
final class AtlassianHttp {

    private AtlassianHttp() {
    }

    static HttpClient newHttpClient(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    static String basicAuth(String email, String token) {
        String credentials = email + ":" + token;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static String urlEncodePathSegment(String value) {
        return urlEncode(value).replace("+", "%20");
    }
}
