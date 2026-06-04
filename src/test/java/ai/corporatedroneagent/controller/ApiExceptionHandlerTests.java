package ai.corporatedroneagent.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ai.corporatedroneagent.dto.ApiErrorDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTests {

    @Test
    void responseStatusExceptionReturnsReasonAsMessage() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/settings/knowledge/local-folders");

        var response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.CONFLICT, "Folders must not be nested inside each other"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        ApiErrorDto body = response.getBody();
        assertThat(body.status()).isEqualTo(409);
        assertThat(body.error()).isEqualTo("Conflict");
        assertThat(body.message()).isEqualTo("Folders must not be nested inside each other");
        assertThat(body.path()).isEqualTo("/api/settings/knowledge/local-folders");
    }
}
