package ai.corporatedroneagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Shared HTTP primitives for the Confluence/Jira REST services: the client builder,
 * basic-auth header, URL encoding, and the authenticated GET plus error mapping that
 * every Jira/Confluence call repeats. The user-facing wording stays with each call
 * (passed in via {@link RequestErrors} / explicit messages) since it is specific to
 * what the call is doing.
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

    /**
     * User-facing messages for the non-2xx responses a discovery/fetch GET can return.
     * {@code notFound} may be {@code null} to let a 404 fall through to the generic message.
     */
    record RequestErrors(
            String requestName,
            String unauthorized,
            String forbidden,
            String notFound,
            String invalidUrl
    ) {
    }

    /**
     * Sends an authenticated GET and returns the raw response. Transport failures are mapped
     * to {@link ResponseStatusException} using the supplied messages; non-2xx status codes are
     * left for the caller to inspect (the validation services need the code, not an exception).
     */
    static HttpResponse<String> send(
            HttpClient httpClient,
            Duration timeout,
            URI uri,
            String email,
            String token,
            String unreachableMessage,
            String interruptedMessage,
            String invalidUrlMessage
    ) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Authorization", basicAuth(email, token))
                .GET()
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, unreachableMessage);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, interruptedMessage);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalidUrlMessage);
        }
    }

    /**
     * Sends an authenticated GET and parses the JSON body, mapping every non-2xx status and
     * transport failure to a {@link ResponseStatusException} with the caller's wording.
     */
    static JsonNode getJson(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            Duration timeout,
            URI uri,
            String email,
            String token,
            RequestErrors errors
    ) {
        String requestFailed = errors.requestName() + " request failed";
        HttpResponse<String> response = send(
                httpClient,
                timeout,
                uri,
                email,
                token,
                requestFailed,
                errors.requestName() + " request was interrupted",
                errors.invalidUrl()
        );
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            try {
                return objectMapper.readTree(response.body());
            } catch (IOException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, requestFailed);
            }
        }
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, errors.unauthorized());
        }
        if (status == HttpStatus.FORBIDDEN.value()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, errors.forbidden());
        }
        if (errors.notFound() != null && status == HttpStatus.NOT_FOUND.value()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, errors.notFound());
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                errors.requestName() + " failed with HTTP " + status
        );
    }
}
